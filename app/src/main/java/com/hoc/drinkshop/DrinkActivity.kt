package com.hoc.drinkshop

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.hoc.drinkshop.MainActivity.Companion.USER
import com.nex3z.notificationbadge.NotificationBadge
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.action_cart_layout.view.*
import kotlinx.android.synthetic.main.activity_drink.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit

class DrinkActivity : AppCompatActivity(), AnkoLogger, (Drink) -> Unit {
    override val loggerTag: String = "MY_DRINK_TAG"

    private lateinit var drinkAdapter: DrinkAdapter
    private lateinit var category: Category
    private val parentJob = Job()

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val cartDataSource by inject<CartDataSource>()

    private lateinit var drinks: MutableList<Drink>
    private lateinit var user: User
    private val compositeDisposable = CompositeDisposable()
    private var badge: NotificationBadge? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drink)

        category = intent.getParcelableExtra(HomeActivity.CATEGORY)
        user = intent.getParcelableExtra(MainActivity.USER)

        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = category.name.toUpperCase()
            setDisplayHomeAsUpEnabled(true)
        }

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

    override fun onStart() {
        super.onStart()
        updateCartCount()
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancelChildren()
    }

    override fun invoke(drink: Drink) = startActivity<AddToCartActivity>(DRINK to drink)

    private fun getDrinks() {
        launch(UI, parent = parentJob) {
            shimmer_layout.visibility = View.VISIBLE
            shimmer_layout.startShimmer()
            recyclerDrink.visibility = View.INVISIBLE

            apiService.getDrinks(menuId = category.id)
                .awaitResult()
                .onSuccess {
                    info(it)
                    drinks = it.toMutableList()
                    drinkAdapter.submitList(drinks)
                    title = category.name.toUpperCase()
                }
                .onException {
                    info(it.message, it)
                    toast("Cannot get drinks because ${it.message ?: "unknown error"}")
                }
                .onError {
                    retrofit.parseResultErrorMessage(it.first)
                        .let { toast("Cannot get drinks because: $it") }
                }
            shimmer_layout.stopShimmer()
            shimmer_layout.visibility = View.INVISIBLE
            recyclerDrink.visibility = View.VISIBLE
            swipeLayout.post { swipeLayout.isRefreshing = false }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_drink_activity, menu)
        menu.findItem(R.id.action_cart).actionView.run {
            this@DrinkActivity.badge = badge
            setOnClickListener {
                startActivity<CartsActivity>(USER to user)
            }
        }

        updateCartCount()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            R.id.action_cart -> true
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)

        }
    }

    private fun updateCartCount() {
        cartDataSource.getCountCart()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { badge?.setNumber(it) },
                onError = { info("getCountCart error: $it") }
            )
            .addTo(compositeDisposable)
    }

    companion object {
        const val DRINK = "drink"
    }
}