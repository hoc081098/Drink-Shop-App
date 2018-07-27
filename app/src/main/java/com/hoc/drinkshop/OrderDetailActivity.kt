package com.hoc.drinkshop

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoc.drinkshop.OrdersActivity.Companion.ORDER
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_order_detail.*
import kotlinx.android.synthetic.main.order_detail_item_layout.view.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.cancelChildren
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import org.koin.android.ext.android.inject
import retrofit2.Retrofit
import java.text.SimpleDateFormat
import java.util.Locale

class OrderDetailActivity : AppCompatActivity(), AnkoLogger {
    override val loggerTag = "MY_ORDER_DETAIL_TAG"

    private val apiService by inject<ApiService>()
    private val retrofit by inject<Retrofit>()
    private val parentJob = Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_detail)

        val order = intent.getParcelableExtra<Order>(ORDER)
        bind(order)

        recycler_order_detail.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@OrderDetailActivity)
            adapter = OrderDetailAdapter(order.detail)
        }

        cancelOrderButton.setOnClickListener {
            cancelOrder(order.id!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        parentJob.cancelChildren()
    }

    private fun cancelOrder(id: Int) {
        launch(context = UI, parent = parentJob) {
            apiService.cancelOrder(id)
                .awaitResult()
                .onException {
                    it.message?.let(::toast)
                }
                .onError { (responseBody, _) ->
                    retrofit.parseResultErrorMessage(responseBody).let(::toast)
                }
                .onSuccess {
                    info(it.status)
                    toast("Cancel order successfully")
                    finish()
                }
        }
    }

    private fun bind(order: Order) = order.run {
        text_id.text = "#${id!!}"
        text_price.text = getString(
            R.string.price,
            DrinkAdapter.decimalFormatPrice.format(price)
        )
        text_comment.text = if (comment.isBlank()) "..." else comment
        text_address.text = address
        text_created_at.text =
            SimpleDateFormat("yyyy-MM-dd, hh:mm", Locale.getDefault()).format(createdAt!!)
        text_status.text = status!!.toString()
        when (status) {
            OrderStatus.PLACED -> R.drawable.ic_fiber_new_black_24dp
            OrderStatus.CANCELED -> R.drawable.ic_cancel_black_24dp
            OrderStatus.PROCESSING -> R.drawable.ic_autorenew_black_24dp
            OrderStatus.SHIPPING -> R.drawable.ic_local_shipping_black_24dp
            OrderStatus.SHIPPED -> R.drawable.ic_done_black_24dp
        }.let { ContextCompat.getDrawable(this@OrderDetailActivity, it) }
            .let {
                TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    text_status,
                    null,
                    null,
                    it,
                    null
                )
            }
    }
}

private class OrderDetailAdapter(private val carts: List<Cart>) :
    RecyclerView.Adapter<OrderDetailAdapter.ViewHolder>() {
    override fun getItemCount() = carts.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(parent inflate R.layout.order_detail_item_layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(carts[position])
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image = itemView.imageDetailItem
        private val title = itemView.textDetailTitle
        private val sugarIce = itemView.textSugarIce
        private val price = itemView.textPrice

        fun bind(cart: Cart) {
            Picasso.get()
                .load(cart.imageUrl)
                .fit()
                .error(R.drawable.ic_image_black_24dp)
                .placeholder(R.drawable.ic_image_black_24dp)
                .into(image)
            title.text = "${cart.name} x${cart.number} size ${cart.cupSize}"
            sugarIce.text = "Sugar: ${cart.sugar}%, ice: ${cart.ice}%"
            price.text = itemView.context.getString(
                R.string.price,
                DrinkAdapter.decimalFormatPrice.format(cart.price)
            )
        }
    }
}