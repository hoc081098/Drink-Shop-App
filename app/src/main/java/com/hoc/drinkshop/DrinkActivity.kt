package com.hoc.drinkshop

import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.view.View
import kotlinx.android.synthetic.main.activity_drink.*
import kotlinx.android.synthetic.main.drink_item_layout.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import android.support.v4.util.Pair as AndroidPair


class DrinkActivity : AppCompatActivity() {
    private val drinkAdapter = DrinkAdapter(::onClick)

    private fun onClick(drink: Drink, itemView: View, clickView: View) {
        when (clickView.id) {
            R.id.imageAddToCart -> {
                val optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                        AndroidPair(itemView.textDrinkName as View, getString(R.string.text_drink_name_trans)),
                        AndroidPair(itemView.imageDrink as View, getString(R.string.image_drink_trans))
                )
                startActivity(
                        intentFor<AddToCartActivity>(TOPPING to topping, DRINK to drink),
                        optionsCompat.toBundle()
                )
            }
            R.id.imageFavorite -> {
                //TODO: TODO()
            }
        }

    }

    lateinit var category: Category
    private val parentJob = Job()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private var topping: ArrayList<Drink>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drink)

        category = intent.getParcelableExtra(HomeActivity.CATEGORY)

        collapsingLayout.title = category.name.toUpperCase()

        recyclerDrink.run {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@DrinkActivity, 2)
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
            apiService.getDrinkByCategoryId("7")
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
            apiService.getDrinkByCategoryId(category.id)
                    .awaitResult()
                    .onSuccess { drinkAdapter.submitList(it) }
                    .onException {
                        toast("Cannot get drinks because ${it.message ?: "unknown error"}")
                    }
                    .onError {
                        retrofit.parse<Error>(it).message.let { toast("Cannot get drinks because: $it") }
                    }
            swipeLayout.post { swipeLayout.isRefreshing = false }
        }
    }

    companion object {
        const val TOPPING = "topping"
        const val DRINK = "drink"
    }
}