package com.hoc.drinkshop

import android.app.Application
import org.koin.android.ext.android.startKoin
import org.koin.dsl.module.module

val appModule = module {
    single { MyApp.app }
}

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        startKoin(this, listOf(retrofitModule, appModule, cartModule))
    }

    companion object {
        lateinit var app: Application
    }
}