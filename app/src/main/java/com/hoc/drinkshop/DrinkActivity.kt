package com.hoc.drinkshop

import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_drink.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import java.util.*
import java.util.concurrent.TimeUnit
import android.support.v4.util.Pair as AndroidPair

class DrinkActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag: String = "MY_DRINK_TAG"

    private lateinit var drinkAdapter: DrinkAdapter

    private fun onButtonAddToCartClick(drink: Drink, @IdRes idClickView: Int) {
        when (idClickView) {
            R.id.imageAddToCart -> {
                startActivity<AddToCartActivity>(TOPPING to topping, DRINK to drink)
            }
            R.id.buttonFav -> { //not handle
                info("Drink before: $drink")
                addToOrRemoveFromFavorites(user.phone, drink, user.phone !in drink.stars)
            }
        }
    }

    private fun addToOrRemoveFromFavorites(phone: String, drink: Drink, add: Boolean) {
        launch(UI, parent = parentJob) {
            val task = if (add) apiService::addStar else apiService::removeStar
            task(phone, drink.id)
                    .awaitResult()
                    .onSuccess { drink ->
                        val index = drinks.indexOfFirst { it.id == drink.id }
                        info("Drink after: $drink,  index = $index")
                        if (index >= 0) {
                            //drinks[index] = drink
                            drinkAdapter.notifyItemChanged(index)
                            toast("${if (add) "Add to" else "Remove from"} successfully")
                        } else {
                            toast("Oops! Drink is not in list")
                        }
                    }
                    .onError {
                        val s = if (add) "add to" else "remove from"
                        retrofit.parseResultErrorMessage(it.first).let { toast("Cannot $s favorite because $it") }
                    }
                    .onException {
                        val s = if (add) "add to" else "remove from"
                        toast("Cannot $s favorite because ${it.message ?: "unknown error"}")
                    }
        }
    }

    private lateinit var category: Category
    private val parentJob = Job()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private var topping: ArrayList<Drink>? = null
    private lateinit var drinks: List<Drink>
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drink)

        category = intent.getParcelableExtra(HomeActivity.CATEGORY)
        user = intent.getParcelableExtra(MainActivity.USER)
        info("user = $user")
        info("category = $category")

        collapsingLayout.title = category.name.toUpperCase()

        recyclerDrink.run {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@DrinkActivity, 2)
            drinkAdapter = DrinkAdapter(::onButtonAddToCartClick, user.phone).apply {
                clickObservable
                        .subscribeOn(AndroidSchedulers.mainThread())
                        .scan(true, { t1: Boolean, _: Type ->
                            !t1
                        })
                        .skip(1)
                        .concatMap { bool ->
                            Observable.timer(1_500, TimeUnit.MILLISECONDS)
                                    .map { bool }

                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeBy {
                            info("subs... $it...")
                        }
            }
            adapter = drinkAdapter
        }

        swipeLayout.setOnRefreshListener {
            getDrinks()
            getTopping()
        }

        getDrinks()
        getTopping()
    }

    private fun getTopping() {
        launch(UI, parent = parentJob) {
            apiService.getDrinks(menuId = 7)
                    .awaitResult()
                    .onSuccess { topping = ArrayList(it) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
    }


    private fun getDrinks() {
        launch(UI, parent = parentJob) {
            apiService.getDrinks(menuId = category.id)
                    .awaitResult()
                    .onSuccess {
                        info(it)
                        drinks = it
                        drinkAdapter.submitList(it)
                    }
                    .onException {
                        info(it.message, it)
                        toast("Cannot get drinks because ${it.message ?: "unknown error"}")
                    }
                    .onError {
                        retrofit.parseResultErrorMessage(it.first).let { toast("Cannot get drinks because: $it") }
                    }
            swipeLayout.post { swipeLayout.isRefreshing = false }
        }
    }

    companion object {
        const val TOPPING = "topping"
        const val DRINK = "drink"
    }
}