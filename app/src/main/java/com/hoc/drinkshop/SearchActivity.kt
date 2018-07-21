package com.hoc.drinkshop

import android.os.Bundle
import android.support.design.widget.BottomSheetBehavior
import android.support.transition.Fade
import android.support.transition.TransitionManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import com.hoc.drinkshop.MainActivity.Companion.USER
import com.miguelcatalan.materialsearchview.MaterialSearchView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.zipWith
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.android.synthetic.main.filter_bottom_sheet.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit
import kotlin.math.ceil
import kotlin.math.floor

class SearchActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag = "MY_SEARCH_TAG"

    private lateinit var searchAdapter: DrinkAdapter
    private var drinks = mutableListOf<Drink>()
    private lateinit var user: User

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()

    private var minPrice = 0f
    private var maxPrice = 0f

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = "Search drink"
            setDisplayHomeAsUpEnabled(true)
        }

        user = intent.getParcelableExtra(USER)

        setupSearchAdapter()

        setupFilterBottomSheet()

        recyclerSearch.run {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@SearchActivity, 2)
            adapter = searchAdapter
        }

        search_view.setOnQueryTextListener(object : MaterialSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })
    }

    private fun performSearch(query: String?) {
        val sortPrice = spinnerSortPrice.selectedItem as SortOrder
        val sortStarCount = spinnerSortStarCount.selectedItem as SortOrder
        val sortName = spinnerSortName.selectedItem as SortOrder
        val limit = spinnerLimit.selectedItem as Int

        info("query=[$query], sortPrice=[$sortPrice], sortStarCount=[$sortStarCount]," +
                " sortName=[$sortName], limit=[$limit], minPrice=[$minPrice], maxPrice=[$maxPrice]")

        launch(UI) {
            TransitionManager.beginDelayedTransition(content_layout, Fade())
            textViewEmpty.visibility = View.GONE
            recyclerSearch.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE

            apiService.getDrinks(
                    name = query,
                    minPrice = minPrice.toDouble(),
                    maxPrice = maxPrice.toDouble(),
                    sortPrice = sortPrice,
                    sortName = sortName,
                    sortStar = sortStarCount,
                    limit = limit
            )
                    .awaitResult()
                    .onError {}
                    .onException {}
                    .onSuccess {
                        drinks = it.toMutableList()

                        TransitionManager.beginDelayedTransition(content_layout, Fade())
                        progressBar.visibility = View.GONE
                        if (drinks.isNotEmpty()) {
                            recyclerSearch.visibility = View.VISIBLE
                            textViewEmpty.visibility = View.GONE

                            searchAdapter.submitList(drinks)
                            toast("Found ${it.size} drinks")
                        } else {
                            textViewEmpty.visibility = View.VISIBLE
                            recyclerSearch.visibility = View.INVISIBLE
                        }
                    }
        }
    }


    private fun onButtonAddToCartClick(drink: Drink) =
            startActivity<AddToCartActivity>(DrinkActivity.DRINK to drink)

    private fun setupSearchAdapter() {
        searchAdapter = DrinkAdapter(::onButtonAddToCartClick, user.phone)
        searchAdapter.clickObservable
                .subscribeOn(AndroidSchedulers.mainThread())
                .concatMap { (drink, adapterPosition) ->
                    when {
                        user.phone in drink.stars -> apiService.unstar(user.phone, drink.id)
                        else -> apiService.star(user.phone, drink.id)
                    }.map { it to adapterPosition }.subscribeOn(Schedulers.io())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onNext = { (drink, adapterPosition) ->
                            drinks[adapterPosition] = drink
                            searchAdapter.notifyItemChanged(adapterPosition)

                            when {
                                user.phone in drink.stars -> "Added to favorite successfully"
                                else -> "Removed from favorite successfully"
                            }.let(::toast)
                        },
                        onError = {
                            info(it.message, it)

                            when (it) {
                                is HttpException -> it.response().errorBody()?.let {
                                    retrofit.parseResultErrorMessage(it)
                                            .let(::toast)
                                }
                                else -> toast(it.message ?: "An error occurred")
                            }
                        }
                )
                .addTo(compositeDisposable)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    private fun setupFilterBottomSheet() {
        buttonFilter.setOnClickListener {
            BottomSheetBehavior.from(filter_bottom_sheet)
                    .state = BottomSheetBehavior.STATE_EXPANDED
        }
        setupRangeSeekbar()
        spinnerLimit.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                (10..100 step 10).toList()
        )
        spinnerLimit.setSelection(0)
        ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(SortOrder.ASC_FULL_STRING, SortOrder.DESC_FULL_STRING)
        ).let {
            spinnerSortName.adapter = it
            spinnerSortName.setSelection(0)
            spinnerSortPrice.adapter = it
            spinnerSortPrice.setSelection(0)
            spinnerSortStarCount.adapter = it
            spinnerSortStarCount.setSelection(0)
        }
    }

    private fun setupRangeSeekbar() {
        apiService.getMaxPrice().zipWith(apiService.getMinPrice())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onNext = { (max, min) ->
                            price_rangebar.run {
                                tickStart = ceil(min.price.toFloat())
                                tickEnd = floor(max.price.toFloat())
                                setTickInterval((tickEnd - tickStart) / 10)
                            }
                        },
                        onError = {}
                )
                .addTo(compositeDisposable)
        price_rangebar.setOnRangeBarChangeListener { _, _, _, leftPinValue, rightPinValue ->
            minPrice = leftPinValue.toFloatOrNull() ?: 0f
            maxPrice = rightPinValue.toFloatOrNull() ?: 0f
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_favorites, menu)
        menu?.findItem(R.id.action_search)?.let(search_view::setMenuItem)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
