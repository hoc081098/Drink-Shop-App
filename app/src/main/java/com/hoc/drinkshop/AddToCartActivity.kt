package com.hoc.drinkshop

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.hoc.drinkshop.DrinkAdapter.Companion.diffCallback
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_add_to_cart.*
import kotlinx.android.synthetic.main.dialog_confirm.view.*
import kotlinx.android.synthetic.main.topping_item_layout.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Retrofit

class ToppingAdapter(private val onClickListener: (Drink, Boolean) -> Unit) : ListAdapter<Drink, ToppingAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(parent inflate R.layout.topping_item_layout)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClickListener)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val toppingCheckBox = itemView.toppingCheckBox!!
        fun bind(item: Drink?, onClickListener: (Drink, Boolean) -> Unit) = item?.name?.let { s ->
            toppingCheckBox.text = s
            toppingCheckBox.setOnCheckedChangeListener { _, isChecked -> onClickListener(item, isChecked) }
        }
    }
}

class AddToCartActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag: String = "MY_ADD_TO_CART_TAG"

    private val parentJob = Job()
    private val compositeDisposable = CompositeDisposable()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val cartDataSource by inject<CartDataSource>()

    private lateinit var drink: Drink
    private val checkedTopping = mutableListOf<Drink>()
    private val toppingAdapter = ToppingAdapter(::onItemCheck)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        setContentView(R.layout.activity_add_to_cart)

        drink = intent.getParcelableExtra(DrinkActivity.DRINK)

        textDrinkName.text = drink.name
        Picasso.with(this)
                .load(drink.imageUrl)
                .fit()
                .error(R.drawable.ic_image_black_24dp)
                .placeholder(R.drawable.ic_image_black_24dp)
                .into(imageView2)

        recyclerTopping.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@AddToCartActivity)
            adapter = toppingAdapter
        }

        getTopping()

        buttonAddToCart.setOnClickListener {

            when {
                radioGroupCupSize.checkedRadioButtonId == -1 -> toast("Please choose cup size")
                radioGroupSugar.checkedRadioButtonId == -1 -> toast("Please choose sugar")
                radioGroupIce.checkedRadioButtonId == -1 -> toast("Please choose ice")
                else -> showConfirmDialog(
                        numberButton.number.toInt(),
                        editTextComment.text.toString(),
                        getCupSize(radioGroupCupSize.checkedRadioButtonId),
                        getSugar(radioGroupSugar.checkedRadioButtonId),
                        getIce(radioGroupIce.checkedRadioButtonId)
                )
            }

        }
    }

    private fun showConfirmDialog(number: Int, comment: String, cupSize: String, sugar: Int, ice: Int) {
        val title = "${drink.name} x$number size $cupSize"
        val totalPrice = number * (drink.price + checkedTopping.sumByDouble { it.price }
                + if (cupSize == "L") 3 else 0)

        val view = layoutInflater.inflate(R.layout.dialog_confirm, null).apply {
            textTitle.text = title
            textPrice.text = "$$totalPrice"
            textSugar.text = "Sugar: $sugar%"
            textIce.text = "Ice: $ice%"
            textToppingExtras.text = checkedTopping.joinToString("\n") { it.name }
            Picasso.with(context)
                    .load(drink.imageUrl)
                    .fit()
                    .error(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageConfirm)
        }

        AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Confirm") { dialog, _ ->
                    dialog.dismiss()

                    val cart = Cart(
                            title,
                            drink.id,
                            number,
                            comment,
                            cupSize,
                            sugar,
                            ice,
                            totalPrice
                    )

                    compositeDisposable += cartDataSource.insertCart(cart)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(onError = {
                                toast("Cannot add to cart because ${it.message ?: "unknown error"}")
                            }, onComplete = {
                                toast("Add to cart success")
                            })

                }
                .create()
                .show()
    }

    private fun getIce(checkedRadioButtonId: Int): Int = when (checkedRadioButtonId) {
        R.id.radioButtonIce100 -> 100
        R.id.radioButtonIce70 -> 70
        R.id.radioButtonIce50 -> 50
        R.id.radioButtonIce30 -> 30
        else -> 0
    }

    private fun getSugar(checkedRadioButtonId: Int): Int = when (checkedRadioButtonId) {
        R.id.radioButtonSugar100 -> 100
        R.id.radioButtonSugar70 -> 70
        R.id.radioButtonSugar50 -> 50
        R.id.radioButtonSugar30 -> 30
        else -> 0
    }

    private fun getCupSize(checkedRadioButtonId: Int): String =
            if (checkedRadioButtonId == R.id.radioButtonSizeL) "L"
            else "M"

    private fun getTopping() {
        launch(UI, parent = parentJob) {
            apiService.getDrinks(menuId = 7)
                    .awaitResult()
                    .onSuccess {
                        toast("getTopping: ${it.size}")
                        toppingAdapter.submitList(it)
                    }.onException {
                        toast("Cannot get topping because ${it.message ?: "unknown error"}")
                    }.onError {
                        toast("Cannot get topping because ${retrofit.parseResultErrorMessage(it.first)}")
                    }
        }
    }


    private fun onItemCheck(s: Drink, isChecked: Boolean) {
        if (isChecked) checkedTopping += s
        else checkedTopping -= s
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
        compositeDisposable.clear()
    }
}