package com.hoc.drinkshop

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.detaches
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.drink_item_layout.view.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.concurrent.TimeUnit

typealias Type = Pair<Drink, Int>

class DrinkAdapter(
        private val onCLickListener: (Drink) -> Unit,
        private val userPhone: String
) : ListAdapter<Drink, DrinkAdapter.ViewHolder>(diffCallback) {
    private val subject = PublishSubject.create<Type>()
    val clickObservable: Observable<Type> = subject

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent inflate R.layout.drink_item_layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View, private val parent: ViewGroup) : RecyclerView.ViewHolder(itemView) {
        private val textDrinkName = itemView.textDrinkName!!
        private val imageDrink = itemView.imageDrink!!
        private val imageAddToCart = itemView.imageAddToCart!!
        private val buttonFav = itemView.buttonFav!!
        private val imageFav = itemView.imageFav!!
        private val textNumberOfStars = itemView.textNumberOfStars!!
        private val textDrinkPrice = itemView.textDrinkPrice!!

        fun bind(item: Drink?) = item?.let { drink ->
            textDrinkName.text = drink.name
            textDrinkPrice.text = itemView.context.getString(R.string.price, decimalFormatPrice.format(drink.price))
            textNumberOfStars.text = decimalFormatStarCount.format(drink.starCount.toLong())
            Picasso.with(imageDrink.context)
                    .load(drink.imageUrl)
                    .fit()
                    .error(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageDrink)

            imageAddToCart.setOnClickListener { onCLickListener(drink) }

            val isFavorite = userPhone in drink.stars
            when {
                isFavorite -> R.drawable.ic_favorite_black_24dp
                else -> R.drawable.ic_favorite_border_black_24dp
            }.let(imageFav::setImageResource)

            buttonFav.clicks()
                    .takeUntil(parent.detaches())
                    .throttleFirst(400, TimeUnit.MILLISECONDS)
                    .map { item to adapterPosition }
                    .subscribe(subject)
        }
    }

    companion object {
        @JvmField
        val diffCallback: DiffUtil.ItemCallback<Drink> = object : DiffUtil.ItemCallback<Drink>() {
            override fun areItemsTheSame(oldItem: Drink?, newItem: Drink?) = oldItem?.id == newItem?.id
            override fun areContentsTheSame(oldItem: Drink?, newItem: Drink?) = oldItem == newItem
        }
        @JvmField
        val decimalFormatPrice = DecimalFormat.getInstance()
        @JvmField
        val decimalFormatStarCount = DecimalFormat("###,###", DecimalFormatSymbols().apply { groupingSeparator = ' ' })
    }
}
