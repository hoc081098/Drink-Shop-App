package com.hoc.drinkshop

import android.app.Application
import org.koin.android.ext.android.startKoin
import org.koin.dsl.module.applicationContext

val appModule = applicationContext {
    bean { MyApp.app }
}

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        startKoin(listOf(retrofitModule, appModule, cartModule))
    }

    companion object {
        lateinit var app: Application
    }
}