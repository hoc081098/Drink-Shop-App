package com.hoc.drinkshop

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.drink_item_layout.view.*
import java.text.DecimalFormat

class DrinkAdapter(private val onCLickListener: (Drink, View, View) -> Unit) : ListAdapter<Drink, DrinkAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            DrinkAdapter.ViewHolder(parent inflate R.layout.drink_item_layout)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onCLickListener)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textDrinkName = itemView.textDrinkName!!
        private val imageDrink = itemView.imageDrink!!
        private val imageAddToCart = itemView.imageAddToCart!!
        private val imageFavorite = itemView.imageFavorite!!
        private val textDrinkPrice = itemView.textDrinkPrice!!

        fun bind(item: Drink?, onCLickListener: (Drink, View, View) -> Unit) = item?.let { drink ->
            textDrinkName.text = drink.name
            textDrinkPrice.text = itemView.context.getString(R.string.price,
                    DecimalFormat.getInstance().format(drink.price.toDouble()))
            Picasso.with(imageDrink.context)
                    .load(drink.imageUrl)
                    .fit()
                    .error(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageDrink)
            imageAddToCart.setOnClickListener { onCLickListener(drink, itemView, it) }
            imageFavorite.setOnClickListener { onCLickListener(drink, itemView, it) }
        }
    }

    companion object {
        @JvmField
        val diffCallback: DiffUtil.ItemCallback<Drink> = object : DiffUtil.ItemCallback<Drink>() {
            override fun areItemsTheSame(oldItem: Drink?, newItem: Drink?) = oldItem?.id == newItem?.id
            override fun areContentsTheSame(oldItem: Drink?, newItem: Drink?) = oldItem == newItem
        }
    }
}