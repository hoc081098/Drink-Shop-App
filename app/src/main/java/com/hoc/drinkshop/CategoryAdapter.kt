package com.hoc.drinkshop

import android.support.annotation.LayoutRes
import android.support.v7.recyclerview.extensions.ListAdapter
import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.daimajia.slider.library.SliderTypes.BaseSliderView
import com.daimajia.slider.library.SliderTypes.TextSliderView
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.category_item_layout.view.*
import kotlinx.android.synthetic.main.slider_item_layout.view.*

infix fun ViewGroup.inflate(@LayoutRes layoutId: Int): View =
        LayoutInflater.from(context).inflate(layoutId, this, false)

class CategoryAdapter(private val onClickListener: (Category) -> Unit)
    : ListAdapter<Any, RecyclerView.ViewHolder>(diffCallback) {
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is SlideViewHolder -> {
                holder.bind(item as? List<*>)
            }
            is TextViewHolder -> Unit
            is CategoryViewHolder -> {
                holder.bind(item as? Category)
            }
            else -> throw IllegalStateException("Uknown view holder!")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SLIDER -> SlideViewHolder(parent inflate R.layout.slider_item_layout)
            TYPE_TEXT -> TextViewHolder(parent inflate R.layout.text_item_layout)
            TYPE_CATEGORY -> CategoryViewHolder(parent inflate R.layout.category_item_layout)
            else -> throw IllegalStateException("Uknown viewType!")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_SLIDER
            1 -> TYPE_TEXT
            else -> TYPE_CATEGORY
        }
    }

    fun submitList(list: List<Category>, slideList: List<Banner>) {
        val mutableList = mutableListOf(slideList, 0)
                .apply { addAll(list) }
        super.submitList(mutableList)
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageCategory = itemView.imageCategory!!
        private val textCategoryName = itemView.textCategoryName!!

        init {
            itemView.setOnClickListener {
                adapterPosition {
                    onClickListener(getItem(it) as Category)
                }
            }
        }

        fun bind(item: Category?) = item?.let { category ->
            Picasso.get()
                    .load(category.imageUrl)
                    .fit()
                    .error(R.drawable.ic_image_black_24dp)
                    .placeholder(R.drawable.ic_image_black_24dp)
                    .into(imageCategory)
            textCategoryName.text = category.name
        }
    }

    class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(list: List<*>?) {
            sliderLayout.removeAllSliders()
            (list ?: return).filterIsInstance(Banner::class.java)
                    .forEach { (_, name, imageUrl) ->
                        TextSliderView(itemView.context)
                                .description(name)
                                .image(imageUrl)
                                .setScaleType(BaseSliderView.ScaleType.Fit)
                                .let(sliderLayout::addSlider)
                    }
        }

        val sliderLayout = itemView.sliderLayout
    }

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        const val TYPE_SLIDER = 0
        const val TYPE_TEXT = 1
        const val TYPE_CATEGORY = 2

        @JvmField
        val diffCallback = object : DiffUtil.ItemCallback<Any>() {
            override fun areItemsTheSame(oldItem: Any?, newItem: Any?) = when {
                oldItem is Category && newItem is Category -> oldItem.id == newItem.id
                else -> oldItem == newItem
            }

            override fun areContentsTheSame(oldItem: Any?, newItem: Any?) = oldItem == newItem
        }
    }
}