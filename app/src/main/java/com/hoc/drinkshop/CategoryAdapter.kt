package com.hoc.drinkshop

import android.support.annotation.LayoutRes
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.category_item_layout.view.*

infix fun ViewGroup.inflate(@LayoutRes layoutId: Int) =
        LayoutInflater.from(context).inflate(layoutId, this, false)

class CategoryAdapter(private val onClickListener: (Category) -> Unit) : ListAdapter<Category, CategoryAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CategoryAdapter.ViewHolder(parent inflate R.layout.category_item_layout)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClickListener)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: Category?, onClickListener: (Category) -> Unit) = item?.let { category ->
            Picasso.with(imageCategory.context)
                    .load(category.imageUrl)
                    .fit()
                    .error(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageCategory)
            textCategoryName.text = category.name
            itemView.setOnClickListener { onClickListener(category) }
        }

        private val imageCategory = itemView.imageCategory!!
        private val textCategoryName = itemView.textCategoryName!!
    }

    companion object {
        @JvmField
        val diffCallback = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(oldItem: Category?, newItem: Category?) = oldItem?.id == newItem?.id
            override fun areContentsTheSame(oldItem: Category?, newItem: Category?) = oldItem == newItem
        }
    }
}