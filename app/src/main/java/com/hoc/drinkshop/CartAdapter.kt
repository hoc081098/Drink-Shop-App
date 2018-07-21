package com.hoc.drinkshop

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.cart_item_layout.view.*

class CartAdapter(private val onNumberChanged: (Cart, Int, Int) -> Unit)
    : ListAdapter<Cart, CartAdapter.ViewHolder>(cartDiffCallback) {
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
                numberButton.setOnValueChangeListener { view, _, newValue ->
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