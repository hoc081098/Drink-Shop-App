package com.hoc.drinkshop

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.view.Menu
import android.view.MenuItem
import com.hoc.drinkshop.MainActivity.Companion.USER
import com.miguelcatalan.materialsearchview.MaterialSearchView
import kotlinx.android.synthetic.main.activity_search.*
import kotlinx.android.synthetic.main.filter_bottom_sheet.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject

class SearchActivity : AppCompatActivity() {
    private lateinit var searchAdapter: DrinkAdapter
    private lateinit var user: User
    private val apiService by inject<ApiService>()

    private fun onClickListener(drink: Drink) {
        toast("click $drink")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = "Search drink"
            setDisplayHomeAsUpEnabled(true)
        }

        user = intent.getParcelableExtra(USER)
        searchAdapter = DrinkAdapter(::onClickListener, user.phone)

        recyclerSearch.run {
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(this@SearchActivity, 2)
            adapter = searchAdapter
        }

        search_view.setOnQueryTextListener(object : MaterialSearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                toast("submit $query")


                launch(UI) {
                    apiService.getDrinks(name = query)
                            .awaitResult()
                            .onError {

                            }
                            .onException {

                            }
                            .onSuccess {
                                searchAdapter.submitList(it)
                            }
                }



                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        crystalRangeSeekbar.setOnRangeSeekbarFinalValueListener { minValue, maxValue ->
            toast("$minValue...$maxValue")
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
