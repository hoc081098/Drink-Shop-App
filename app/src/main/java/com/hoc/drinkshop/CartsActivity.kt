package com.hoc.drinkshop

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_carts.*
import kotlinx.android.synthetic.main.cart_item_layout.view.*
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject

class CartsActivity : AppCompatActivity() {
    private val cartDataSource by inject<CartDataSource>()
    private val cartAdapter = CartAdapter(::onNumberChanged)
    private var carts = mutableListOf<Cart>()
    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_carts)

        recycler_carts.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@CartsActivity)
            adapter = cartAdapter
            ItemTouchHelper(ItemTouchHelperCallback(::onSwiped)).attachToRecyclerView(this)
        }

        getCarts()
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

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
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

class CartAdapter(private val onNumberChanged: (Cart, Int, Int) -> Unit) : ListAdapter<Cart, CartAdapter.ViewHolder>(cartDiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context)
                .inflate(R.layout.cart_item_layout, parent, false)
                .let(::ViewHolder)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(getItem(position), onNumberChanged)

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCartItem = itemView.imageCartItem
        private val textCartTitle = itemView.textCartTitle
        private val textSugarIce = itemView.textSugarIce
        private val textPrice = itemView.textPrice
        private val numberButton = itemView.numberButton
        val swipableView = itemView.foreground_layout

        fun bind(item: Cart?, onNumberChanged: (Cart, Int, Int) -> Unit) {
            item?.let { cart ->
                Picasso.get()
                        .load(cart.imageUrl)
                        .fit()
                        .error(R.drawable.ic_image_black_24dp)
                        .placeholder(R.drawable.ic_image_black_24dp)
                        .into(imageCartItem)
                textCartTitle.text = "${cart.name} x${cart.number} size ${cart.cupSize}"
                textSugarIce.text = "Sugar: ${cart.sugar}%, ice: ${cart.ice}%"
                textPrice.text = itemView.context.getString(R.string.price, DrinkAdapter.decimalFormatPrice.format(cart.price))

                numberButton.number = cart.number.toString()
                numberButton.setOnValueChangeListener { view, oldValue, newValue ->
                    onNumberChanged(cart, adapterPosition, newValue)
                }
            }
        }
    }

    companion object {
        val cartDiffCallback = object : DiffUtil.ItemCallback<Cart>() {
            override fun areItemsTheSame(oldItem: Cart?, newItem: Cart?) = oldItem?.id == newItem?.id
            override fun areContentsTheSame(oldItem: Cart?, newItem: Cart?) = oldItem == newItem
        }
    }
}
