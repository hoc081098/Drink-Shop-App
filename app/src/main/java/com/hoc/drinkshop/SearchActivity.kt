package com.hoc.drinkshop

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
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

class SearchActivity : AppCompatActivity(), AnkoLogger, (Drink) -> Unit {
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
                return false
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
            //TransitionManager.beginDelayedTransition(content_layout, Fade())
            textViewEmpty.visibility = View.INVISIBLE
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

                        //TransitionManager.beginDelayedTransition(content_layout, Fade())
                        progressBar.visibility = View.INVISIBLE
                        if (drinks.isNotEmpty()) {
                            recyclerSearch.visibility = View.VISIBLE
                            textViewEmpty.visibility = View.INVISIBLE

                            searchAdapter.submitList(drinks)
                            toast("Found ${it.size} drinks")
                        } else {
                            textViewEmpty.visibility = View.VISIBLE
                            recyclerSearch.visibility = View.INVISIBLE
                        }
                    }
        }
    }

    override fun invoke(drink: Drink) = startActivity<AddToCartActivity>(DrinkActivity.DRINK to drink)

    private fun setupSearchAdapter() {
        searchAdapter = DrinkAdapter(this, user.phone)
        searchAdapter.clickObservable
                .concatMap { (pos, drink) ->
                    val task = when {
                        user.phone in drink.stars -> apiService.unstar(user.phone, drink.id)
                        else -> apiService.star(user.phone, drink.id)
                    }
                    task.map { it to pos }
                            .subscribeOn(Schedulers.io())
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
                            when (it) {
                                is HttpException -> it.response()
                                        .errorBody()
                                        ?.let(retrofit::parseResultErrorMessage)
                                else -> it.message
                            }.let { it ?: "An error occurred" }.let(::toast)
                        }
                )
                .addTo(compositeDisposable)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }

    private fun setupFilterBottomSheet() {
        val bottomSheetBehavior = BottomSheetBehavior.from(filter_bottom_sheet)
        val upDrawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_up_black_24dp)
        val dowwnDrawable = ContextCompat.getDrawable(this, R.drawable.ic_arrow_drop_down_black_24dp)

        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(p0: View, p1: Float) = Unit

            override fun onStateChanged(v: View, state: Int) {
                if (state in listOf(STATE_COLLAPSED, STATE_EXPANDED))
                    TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            buttonFilter,
                            null,
                            null,
                            when (state) {
                                STATE_COLLAPSED -> upDrawable
                                else -> dowwnDrawable
                            },
                            null
                    )
            }
        })

        buttonFilter.setOnClickListener {
            bottomSheetBehavior.run {
                state = when (state) {
                    STATE_COLLAPSED -> STATE_EXPANDED
                    STATE_EXPANDED -> STATE_COLLAPSED
                    else -> return@setOnClickListener
                }
            }
        }

        setupRangeSeekbar()
        spinnerLimit.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                (10..100 step 10).toList()
        )
        spinnerLimit.setSelection(1)
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


    override fun onBackPressed() {
        if (search_view.isSearchOpen) {
            search_view.closeSearch()
        } else {
            super.onBackPressed()
        }
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
