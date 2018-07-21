package com.hoc.drinkshop

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.hoc.drinkshop.DrinkAdapter.Companion.decimalFormatPrice
import com.hoc.drinkshop.MainActivity.Companion.USER
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_carts.*
import kotlinx.android.synthetic.main.submit_order_layout.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit

class CartsActivity : AppCompatActivity() {
    private val cartDataSource by inject<CartDataSource>()
    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()

    private val cartAdapter = CartAdapter(::onNumberChanged)
    private var carts = mutableListOf<Cart>()
    private var totalPrice = 0.0
    private val compositeDisposable = CompositeDisposable()
    private val parentJob = Job()
    private lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_carts)

        user = intent.getParcelableExtra<User>(USER)

        recycler_carts.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@CartsActivity)
            adapter = cartAdapter
            ItemTouchHelperCallback(::onSwiped).let(::ItemTouchHelper)
                    .attachToRecyclerView(this)
        }

        subscribe()

        buttonPlaceOrder.setOnClickListener { submitOrder() }
    }

    override fun onStart() {
        super.onStart()
        subscribe()
    }

    private fun subscribe() {
        getCarts()

        cartDataSource.getCountCart()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = { toast(it.message ?: "Uknown error occurred") },
                        onNext = {
                            badge.setNumber(it)
                            buttonPlaceOrder.isEnabled = it > 0
                            buttonPlaceOrder.isClickable = it > 0
                            buttonPlaceOrder.translationZ = if (it > 0) 4f else 0f
                        }
                )
                .addTo(compositeDisposable)
        cartDataSource.getSumPrice()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = { toast(it.message ?: "Uknown error occurred") },
                        onNext = {
                            val price = it.firstOrNull() ?: 0.0
                            textTotalPrice.text = "Total $${decimalFormatPrice.format(price)}"
                            totalPrice = price
                        }
                )
    }

    private fun submitOrder() {
        @SuppressLint("InflateParams")
        val view = layoutInflater.inflate(R.layout.submit_order_layout, null)
        val editTextOtherAddress = view.editTextOtherAddress
        view.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            editTextOtherAddress.isEnabled = checkedId == R.id.radioOtherAdd
        }

        AlertDialog.Builder(this)
                .setTitle("Submit order")
                .setView(view)
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Ok") { dialog, _ ->
                    dialog.dismiss()

                    val address = if (editTextOtherAddress.isEnabled) {
                        editTextOtherAddress.text.toString()
                    } else {
                        user.address
                    }
                    if (address.isBlank()) {
                        toast("Please input address")
                        return@setPositiveButton
                    }
                    val comment = view.editTextComment.text.toString()

                    launch(UI, parent = parentJob) {
                        Order(
                                detail = carts,
                                price = totalPrice,
                                phone = user.phone,
                                address = address,
                                comment = comment
                        ).let(apiService::submitOrder)
                                .subscribeOn(Schedulers.io())
                                .flatMapCompletable {
                                    cartDataSource.deleteAllCart().subscribeOn(Schedulers.io())
                                }
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeBy(
                                        onError = {
                                            when (it) {
                                                is HttpException -> it.response()
                                                        .errorBody()
                                                        ?.let(retrofit::parseResultErrorMessage)
                                                else -> it.message
                                            }.let { it ?: "An error occurred" }.let(::toast)
                                        },
                                        onComplete = { toast("Submit order successfully") }
                                )
                    }
                }
                .create()
                .show()
    }

    private fun onSwiped(viewHolder: RecyclerView.ViewHolder) {
        if (viewHolder !is CartAdapter.ViewHolder) return

        val position = viewHolder.adapterPosition
        if (position !in carts.indices) return

        val cart = carts[position]

        cartDataSource.deleteCart(cart)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = {
                            toast("Delete error: ${it.message ?: "unknown error"}")
                        },
                        onComplete = {

                            Snackbar.make(carts_layout, "Delete successfully", Snackbar.LENGTH_SHORT)
                                    .setAction("UNDO") {

                                        cartDataSource.insertCart(cart)
                                                .subscribeOn(Schedulers.io())
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribeBy(
                                                        onError = {
                                                            toast("Undo error: ${it.message
                                                                    ?: "unknown error"}")
                                                        },
                                                        onComplete = {
                                                            toast("Undo successfully")
                                                        }
                                                )
                                                .addTo(compositeDisposable)

                                    }
                                    .show()

                        }
                )
                .addTo(compositeDisposable)
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancel()
    }

    private fun getCarts() {
        cartDataSource.getAllCart()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = { toast("Get carts error: ${it.message ?: "unknown error"}") },
                        onNext = {
                            carts = it.toMutableList()
                            cartAdapter.submitList(carts)
                        }
                )
                .addTo(compositeDisposable)
    }

    private fun onNumberChanged(cart: Cart, adapterPosition: Int, newValue: Int) {
        val totalPrice = cart.price / cart.number * newValue
        val updated = cart.copy(number = newValue, price = totalPrice)
        cartDataSource.updateCart(updated)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                        onError = {
                            toast("Update number error: ${it.message ?: "unknown error"}")
                        },
                        onComplete = {
                            carts[adapterPosition] = updated
                            cartAdapter.notifyItemChanged(adapterPosition)
                        }
                )
                .addTo(compositeDisposable)
    }
}


