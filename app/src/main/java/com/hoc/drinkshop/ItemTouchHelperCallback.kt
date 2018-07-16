package com.hoc.drinkshop

import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper


class ItemTouchHelperCallback(private val onSwiped: (RecyclerView.ViewHolder) -> Unit) : ItemTouchHelper.Callback() {
    override fun onMove(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, target: RecyclerView.ViewHolder?): Boolean {
        return false
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (direction == ItemTouchHelper.START || direction == ItemTouchHelper.END) {
            onSwiped(viewHolder)
        }
    }

    override fun getMovementFlags(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?): Int {
        return when (viewHolder) {
            is FavoritesAdapter.ViewHolder -> {
                makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.START or ItemTouchHelper.END)
            }
            is CartAdapter.ViewHolder -> {
                makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.START or ItemTouchHelper.END)
            }
            else -> 0
        }
    }

    override fun onChildDraw(c: Canvas?, recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        when (viewHolder) {
            is FavoritesAdapter.ViewHolder -> getDefaultUIUtil().onDraw(c, recyclerView, viewHolder.swipableView, dX, dY, actionState, isCurrentlyActive)
            is CartAdapter.ViewHolder -> getDefaultUIUtil().onDraw(c, recyclerView, viewHolder.swipableView, dX, dY, actionState, isCurrentlyActive)
            else -> super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onChildDrawOver(c: Canvas?, recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        when (viewHolder) {
            is FavoritesAdapter.ViewHolder -> getDefaultUIUtil().onDrawOver(c, recyclerView, viewHolder.swipableView, dX, dY, actionState, isCurrentlyActive)
            is CartAdapter.ViewHolder -> getDefaultUIUtil().onDrawOver(c, recyclerView, viewHolder.swipableView, dX, dY, actionState, isCurrentlyActive)
            else -> super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        when (viewHolder) {
            is FavoritesAdapter.ViewHolder -> getDefaultUIUtil().onSelected(viewHolder.swipableView)
            is CartAdapter.ViewHolder -> getDefaultUIUtil().onSelected(viewHolder.swipableView)
            else -> super.onSelectedChanged(viewHolder, actionState)
        }
    }

    override fun clearView(recyclerView: RecyclerView?, viewHolder: RecyclerView.ViewHolder?) {
        when (viewHolder) {
            is FavoritesAdapter.ViewHolder -> getDefaultUIUtil().clearView(viewHolder.swipableView)
            is CartAdapter.ViewHolder -> getDefaultUIUtil().clearView(viewHolder.swipableView)
            else -> super.clearView(recyclerView, viewHolder)
        }
    }
}