package com.hoc.drinkshop

import android.os.Looper
import android.view.MenuItem
import androidx.annotation.CheckResult
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.MainThreadDisposable
import io.reactivex.disposables.Disposables

fun checkMainThread(observer: Observer<*>): Boolean {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        observer.onSubscribe(Disposables.empty())
        observer.onError(
            IllegalStateException(
                "Expected to be called on the main thread but was " + Thread.currentThread().name
            )
        )
        return false
    }
    return true
}

class BottomNavigationViewItemSelectionsObservable(private val view: BottomNavigationView) :
    Observable<MenuItem>() {

    override fun subscribeActual(observer: Observer<in MenuItem>) {
        if (!checkMainThread(observer)) {
            return
        }
        val listener = Listener(view, observer)
        observer.onSubscribe(listener)
        view.setOnNavigationItemSelectedListener(listener)

        // Emit initial item, if one can be found
        val menu = view.menu
        (0 until menu.size())
            .map { menu.getItem(it) }
            .find { it.isChecked }
            ?.let(observer::onNext)
    }

    internal class Listener(
        private val bottomNavigationView: BottomNavigationView,
        private val observer: Observer<in MenuItem>
    ) : MainThreadDisposable(), BottomNavigationView.OnNavigationItemSelectedListener {

        override fun onNavigationItemSelected(item: MenuItem): Boolean {
            if (!isDisposed) {
                observer.onNext(item)
            }
            return true
        }

        override fun onDispose() = bottomNavigationView.setOnNavigationItemSelectedListener(null)
    }
}

@CheckResult
fun BottomNavigationView.itemSelections(): Observable<MenuItem> =
    BottomNavigationViewItemSelectionsObservable(this)