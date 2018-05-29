package com.hoc.drinkshop

import org.koin.dsl.module.applicationContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

val retrofitModule = applicationContext {
    bean<Retrofit> {
        Retrofit.Builder()
                .addConverterFactory(MoshiConverterFactory.create())
                .baseUrl(BASE_URL)
                .build()
    }
    bean<ApiService> {
        get<Retrofit>().create(ApiService::class.java)
    }
}
