package com.hoc.drinkshop

import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.topping_item_layout.view.*

class ToppingAdapter : ListAdapter<Drink, ToppingAdapter.ViewHolder>(DrinkAdapter.diffCallback) {
    private val mutableCheckedTopping = hashSetOf<Drink>()
    val checkedTopping: Iterable<Drink> get() = mutableCheckedTopping

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(parent inflate R.layout.topping_item_layout)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind = true
        holder.bind(getItem(position))
        holder.bind = false
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var bind = false

        val toppingCheckBox = itemView.toppingCheckBox!!.apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (bind) return@setOnCheckedChangeListener

                adapterPosition {
                    val drink = getItem(it)
                    if (isChecked) mutableCheckedTopping += drink
                    else mutableCheckedTopping -= drink
                    notifyItemChanged(it)
                }
            }
        }

        fun bind(drink: Drink) = toppingCheckBox.run {
            text = drink.name
            isChecked = drink in mutableCheckedTopping
        }
    }
}