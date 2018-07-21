package com.hoc.drinkshop

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_drink.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit
import android.support.v4.util.Pair as AndroidPair

class DrinkActivity : AppCompatActivity(), AnkoLogger, (Drink) -> Unit {
    override val loggerTag: String = "MY_DRINK_TAG"

    private lateinit var drinkAdapter: DrinkAdapter
    private lateinit var category: Category
    private val parentJob = Job()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private lateinit var drinks: MutableList<Drink>
    private lateinit var user: User
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drink)

        category = intent.getParcelableExtra(HomeActivity.CATEGORY)
        user = intent.getParcelableExtra(MainActivity.USER)
        info("user = $user")
        info("category = $category")

        collapsingLayout.title = category.name.toUpperCase()

        drinkAdapter = DrinkAdapter(this, user.phone)
        drinkAdapter.clickObservable
                .concatMap { (adapterPosition, drink) ->
                    info("concatMap => $drink, $adapterPosition")
                    val task = when {
                        user.phone in drink.stars -> apiService.unstar(user.phone, drink.id)
                        else -> apiService.star(user.phone, drink.id)
                    }
                    task.map { it to adapterPosition }
                            .subscribeOn(Schedulers.io())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onNext = { (drink, adapterPosition) ->
                            info("onNext => $drink, $adapterPosition")

                            drinks[adapterPosition] = drink
                            drinkAdapter.notifyItemChanged(adapterPosition)

                            when {
                                user.phone in drink.stars -> "Added to favorite successfully"
                                else -> "Removed from favorite successfully"
                            }.let(::toast)
                        },
                        onError = {
                            when (it) {
                                is HttpException -> it.response()
                                        .errorBody()
                                        ?.let(retrofit::parseResultErrorMessage)
                                else -> it.message
                            }.let { it ?: "An error occurred" }.let(::toast)
                        }
                )
                .addTo(compositeDisposable)

        recyclerDrink.run {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@DrinkActivity, 2)
            adapter = drinkAdapter
        }

        swipeLayout.setOnRefreshListener { getDrinks() }
        getDrinks()
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
        compositeDisposable.clear()
    }

    override fun invoke(drink: Drink) = startActivity<AddToCartActivity>(DRINK to drink)

    private fun getDrinks() {
        launch(UI, parent = parentJob) {
            apiService.getDrinks(menuId = category.id)
                    .awaitResult()
                    .onSuccess {
                        info(it)
                        drinks = it.toMutableList()
                        drinkAdapter.submitList(drinks)
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
        const val DRINK = "drink"
    }
}