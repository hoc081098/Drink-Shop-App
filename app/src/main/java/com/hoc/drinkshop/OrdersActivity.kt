package com.hoc.drinkshop

import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hoc.drinkshop.DrinkAdapter.Companion.decimalFormatPrice
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_orders.*
import kotlinx.android.synthetic.main.order_item_layout.view.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.HttpException
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Locale

class OrdersActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag = "MY_ORDERS_TAG"

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val orderAdapter = OrderAdapter(::onClickListener)

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)

        recycler_orders.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@OrdersActivity)
            adapter = orderAdapter
        }

        bottom_nav.selectedItemId = R.id.status_placed
    }

    override fun onStart() {
        super.onStart()
        compositeDisposable += bottom_nav.itemSelections()
            .map {
                when (it.itemId) {
                    R.id.status_placed -> OrderStatus.PLACED
                    R.id.status_processing -> OrderStatus.PROCESSING
                    R.id.status_shipping -> OrderStatus.SHIPPING
                    R.id.status_shipped -> OrderStatus.SHIPPED
                    R.id.status_canceled -> OrderStatus.CANCELED
                    else -> throw IllegalStateException()
                }
            }
            .switchMap {
                apiService.getOrderByStatus(it)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe {
                        info("doOnSubscribe: ${Looper.myLooper() != Looper.getMainLooper()}")
                        info("doOnSubscribe: ${Thread.currentThread()}")
                        progressBar.visibility = View.VISIBLE
                    }
                    .doOnTerminate {
                        info("doOnTerminate: ${Looper.myLooper() != Looper.getMainLooper()}")
                        info("doOnTerminate: ${Thread.currentThread()}")
                        progressBar.visibility = View.INVISIBLE
                    }
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
                onNext = {
                    orderAdapter.submitList(it)
                }
            )
    }

    private fun onClickListener(order: Order) {
        toast(order.id.toString())
    }

    override fun onStop() {
        super.onStop()
        compositeDisposable.clear()
    }
}

class OrderAdapter(private val onClickListener: (Order) -> Unit) :
    ListAdapter<Order, OrderAdapter.ViewHolder>(diffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.order_item_layout, parent, false)
            .let(::ViewHolder)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        private val textId = itemView.text_id!!
        private val textPrice = itemView.text_price!!
        private val textComment = itemView.text_comment!!
        private val textAddress = itemView.text_address!!
        private val textCreatedAt = itemView.text_created_at!!
        private val textStatus = itemView.text_status!!

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View) {
            adapterPosition { onClickListener(getItem(it)) }
        }

        fun bind(order: Order) = order.run {
            textId.text = "#${id!!}"
            textPrice.text = itemView.context.getString(
                R.string.price,
                decimalFormatPrice.format(price)
            )
            textComment.text = if (comment.isBlank()) "..." else comment
            textAddress.text = address
            textCreatedAt.text =
                SimpleDateFormat("yyyy-MM-dd, hh:mm", Locale.getDefault()).format(createdAt!!)
            textStatus.text = status!!.toString()
            when (status) {
                OrderStatus.PLACED -> R.drawable.ic_fiber_new_black_24dp
                OrderStatus.CANCELED -> R.drawable.ic_cancel_black_24dp
                OrderStatus.PROCESSING -> R.drawable.ic_autorenew_black_24dp
                OrderStatus.SHIPPING -> R.drawable.ic_local_shipping_black_24dp
                OrderStatus.SHIPPED -> R.drawable.ic_done_black_24dp
            }.let { ContextCompat.getDrawable(itemView.context, it) }
                .let {
                    TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        textStatus,
                        null,
                        null,
                        it,
                        null
                    )
                }
        }
    }

    companion object {
        @JvmField
        val diffCallback = object : DiffUtil.ItemCallback<Order>() {
            override fun areItemsTheSame(oldItem: Order, newItem: Order) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Order, newItem: Order) = oldItem == newItem
        }
    }
}