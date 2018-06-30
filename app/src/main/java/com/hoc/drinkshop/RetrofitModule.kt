package com.hoc.drinkshop

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module.applicationContext
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.*

val retrofitModule = applicationContext {
    bean<Moshi> {
        Moshi.Builder()
                .add(Date::class.java, Rfc3339DateJsonAdapter())
                .build()
    }

    bean { MoshiConverterFactory.create(get()) } bind Converter.Factory::class

    bean<Interceptor> {
        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    bean<OkHttpClient> {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(get())
        }

        builder.build()
    }

    bean<Retrofit> {
        Retrofit.Builder()
                .addConverterFactory(get())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .client(get())
                .baseUrl(BASE_URL)
                .build()
    }
    bean<ApiService> {
        get<Retrofit>().create(ApiService::class.java)
    }
}
