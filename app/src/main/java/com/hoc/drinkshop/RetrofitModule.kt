package com.hoc.drinkshop

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module.module
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date

val retrofitModule = module {
    single<Moshi> {
        Moshi.Builder()
            .add(Date::class.java, Rfc3339DateJsonAdapter())
            .build()
    }

    single { MoshiConverterFactory.create(get()) } bind Converter.Factory::class

    single<Interceptor> {
        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    single<OkHttpClient> {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(get())
        }

        builder.build()
    }

    single<Retrofit> {
        Retrofit.Builder()
            .addConverterFactory(get())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(get())
            .baseUrl(BASE_URL)
            .build()
    }
    single<ApiService> {
        get<Retrofit>().create(ApiService::class.java)
    }
}
