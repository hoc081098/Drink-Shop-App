package com.hoc.drinkshop

import android.os.Bundle
import android.support.annotation.IdRes
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.hoc.drinkshop.DrinkActivity.Companion.DRINK
import com.hoc.drinkshop.DrinkActivity.Companion.TOPPING
import com.hoc.drinkshop.DrinkAdapter.Companion.decimalFormatPrice
import com.hoc.drinkshop.DrinkAdapter.Companion.diffCallback
import com.hoc.drinkshop.MainActivity.Companion.USER
import com.miguelcatalan.materialsearchview.MaterialSearchView
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_favorites.*
import kotlinx.android.synthetic.main.fav_item_layout.view.*
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

class FavoritesAdapter(private val onClickListener: (Drink, Int, Int) -> Unit) : ListAdapter<Drink, FavoritesAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent inflate R.layout.fav_item_layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClickListener)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName = itemView.textName
        private val imageView = itemView.imageView
        private val cardView = itemView.cardView
        private val textPrice = itemView.textPrice
        private val imageFav = itemView.imageFav
        val swipableView = itemView.background_layout!!

        fun bind(item: Drink?, onClickListener: (Drink, Int, Int) -> Unit) = item?.let { drink ->
            textName.text = drink.name
            textPrice.text = itemView.context.getString(R.string.price, decimalFormatPrice.format(drink.price))
            Picasso.with(imageView.context)
                    .load(drink.imageUrl)
                    .fit()
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .error(R.drawable.ic_image_black_24dp)
                    .into(imageView)

            val listener = View.OnClickListener { onClickListener(drink, it.id, adapterPosition) }
            cardView.setOnClickListener(listener)
            imageFav.setOnClickListener(listener)

            imageFav.setImageResource(R.drawable.ic_favorite_black_24dp)
        }
    }
}

class FavoritesActivity : AppCompatActivity(), AnkoLogger {
    private val favoritesAdapter = FavoritesAdapter(::onDrinkClick)
    private val parentJob = Job()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private lateinit var drinks: MutableList<Drink>
    private lateinit var user: User
    private val compositeDisposable = CompositeDisposable()
    private var topping: ArrayList<Drink>? = null

    private fun removeFromFavorites(phone: String, drink: Drink, adapterPosition: Int) {
        launch(UI, parent = parentJob) {
            apiService.removeStar(phone, drink.id)
                    .awaitResult()
                    .onSuccess { drink ->
                        assert(drinks[adapterPosition].id == drink.id) { "drinks[adapterPosition].id == drink.id failed" }
                        drinks.removeAt(adapterPosition)
                        favoritesAdapter.notifyItemRemoved(adapterPosition)
                    }
                    .onError {
                        retrofit.parseResultErrorMessage(it.first).let { toast("Cannot remove from favorite because $it") }
                    }
                    .onException {
                        toast("Cannot remove from  because ${it.message ?: "unknown error"}")
                    }
        }
    }

    private fun onDrinkClick(drink: Drink, @IdRes clickId: Int, adapterPosition: Int) {
        when (clickId) {
            R.id.cardView -> startActivity<AddToCartActivity>(TOPPING to topping, DRINK to drink)
            R.id.imageFav -> {
                info("Drink before: $drink")
                removeFromFavorites(user.phone, drink, adapterPosition)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = "My favorite drinks"
            setDisplayHomeAsUpEnabled(true)
        }

        user = intent.getParcelableExtra(USER)

        recycler_favorites.run {
            layoutManager = LinearLayoutManager(this@FavoritesActivity)
            adapter = favoritesAdapter
            setHasFixedSize(true)
            ItemTouchHelper(ItemTouchHelperCallback(::onSwiped)).attachToRecyclerView(this)
        }

        //getFavoriteDrinks()
        getTopping()

        val processor = PublishProcessor.create<String>()
        search_view.setOnQueryTextListener(object : MaterialSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true

            override fun onQueryTextChange(newText: String?) = newText?.let {
                processor.onNext(it)
                true
            } ?: false
        })
        processor.subscribeOn(Schedulers.io())
                .debounce(300, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .map { it.toLowerCase() }
                .switchMap { queryString ->
                    apiService
                            .getDrinksFlowable(
                                    name = if (queryString.isBlank()) null else queryString,
                                    phone = user.phone
                            )
                            .subscribeOn(Schedulers.io())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = {
                            toast("Cannot get favorite drinks because ${it.message
                                    ?: "unknown error"}")
                        },
                        onNext = {
                            info("onNext: ${it.size}")
                            drinks = it.toMutableList()
                            favoritesAdapter.submitList(it)
                        }
                )
                .addTo(compositeDisposable)
        processor.onNext("")
    }

    private fun onSwiped(position: Int) {
        toast("onSwiped $position")
    }

    override fun onBackPressed() {
        if (search_view.isSearchOpen) {
            search_view.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_favorites, menu)
        menu?.findItem(R.id.action_search).let { search_view.setMenuItem(it) }
        return true
    }

    /*private fun getFavoriteDrinks() {
        launch(UI, parent = parentJob) {
            apiService.getDrinks(phone = user.phone)
                    .awaitResult()
                    .onSuccess {
                        drinks.clear()
                        drinks += it
                        favoritesAdapter.submitList(drinks)
                    }
                    .onError {
                        toast("Cannot get favorite drinks because ${retrofit.parseResultErrorMessage(it.first)}")
                    }
                    .onException {
                        toast("Cannot get favorite drinks because ${it.message ?: "unknown error"}")
                    }
        }
    }*/

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
        compositeDisposable.clear()
    }
}


