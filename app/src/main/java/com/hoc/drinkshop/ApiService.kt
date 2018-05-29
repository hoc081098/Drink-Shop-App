package com.hoc.drinkshop

import android.os.Parcelable
import com.facebook.accountkit.Account
import com.facebook.accountkit.AccountKit
import com.facebook.accountkit.AccountKitCallback
import com.facebook.accountkit.AccountKitError
import com.squareup.moshi.Json
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import okhttp3.MultipartBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.*
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

const val BASE_URL = "https://drink-shop.herokuapp.com/api/"

data class CheckUserResponse(val isExists: Boolean)

data class Error(val message: String)

data class RegisterResponse(val message: String, val isSuccessful: Boolean)

@Parcelize
data class User(
        val phone: String,
        val name: String,
        val birthday: String,
        val address: String,
        val imageUrl: String? = null
) : Parcelable

data class Banner(val name: String, val imageUrl: String)

@Parcelize
data class Category(
        @field:Json(name = "_id") var id: String,
        val name: String,
        val imageUrl: String
) : Parcelable

@Parcelize
data class Drink(
        @field:Json(name = "_id") var id: String,
        val name: String,
        val imageUrl: String,
        val price: String,
        val menuId: String
) : Parcelable

data class UploadImageResponse(val message: String, val imageUri: String? = null)

interface ApiService {
    @FormUrlEncoded
    @POST("checkuser")
    fun checkUserIsExist(@Field("phone") phone: String): Call<CheckUserResponse>

    @FormUrlEncoded
    @POST("register")
    fun register(
            @Field("phone") phone: String,
            @Field("name") name: String,
            @Field("birthday") birthday: String,
            @Field("address") address: String
    ): Call<RegisterResponse>

    @GET("user")
    fun getUserInfomation(@Query("phone") phone: String): Call<User>

    @GET("banners")
    fun getBanners(@Query("limit") limit: Int? = null): Call<List<Banner>>

    @GET("drinks/{menu_id}")
    fun getDrinkByCategoryId(@Path("menu_id") menuId: String): Call<List<Drink>>

    @GET("categories")
    fun getAllCategories(): Call<List<Category>>

    @POST("image")
    @Multipart
    fun uploadImage(
            @Part body: MultipartBody.Part,
            @Part("phone") phone: String
    ): Call<UploadImageResponse>
}


sealed class Result<out T : Any> {
    data class Success<out T : Any>(
            val value: T,
            val response: Response
    ) : Result<T>()

    data class Error(val errorBody: ResponseBody) : Result<Nothing>()

    data class Exception(val throwable: Throwable) : Result<Nothing>()
}

fun <T : Any> Result<T>.getOrNull(): T? = when (this) {
    is Result.Success -> value
    else -> null
}

inline fun <T : Any> Result<T>.getOrDefault(crossinline default: () -> T): T = getOrNull()
        ?: default()

fun <T : Any> Result<T>.getOrDefault(default: T): T = getOrDefault { default }


inline fun <T : Any, R : Any> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(value), response)
    is Result.Error -> this
    is Result.Exception -> this
}

inline fun <T : Any> Result<T>.onSuccess(onSuccess: (T) -> Unit): Result<T> {
    getOrNull()?.let(onSuccess)
    return this
}

inline fun <T : Any> Result<T>.onError(onError: (ResponseBody) -> Unit): Result<T> {
    if (this is Result.Error) onError(errorBody)
    return this
}

inline fun <T : Any> Result<T>.onException(onException: (Throwable) -> Unit): Result<T> {
    if (this is Result.Exception) onException(throwable)
    return this
}

suspend fun <T : Any> Call<T>.await(): T = suspendCancellableCoroutine { continuation: CancellableContinuation<T> ->
    enqueue(object : Callback<T> {
        override fun onFailure(call: Call<T>?, t: Throwable) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(t)
        }

        override fun onResponse(call: Call<T>?, response: retrofit2.Response<T>) {
            when {
                response.isSuccessful -> {
                    val body: T? = response.body()
                    if (body != null) {
                        continuation.resume(body)
                    } else {
                        continuation.resumeWithException(NullPointerException("Reponse body is null"))
                    }
                }
                else -> {
                    continuation.resumeWithException(HttpException(response))
                }
            }
        }
    })

    registerOnComletion(continuation)
}

fun <T> Call<*>.registerOnComletion(continuation: CancellableContinuation<T>) {
    continuation.invokeOnCompletion {
        if (continuation.isCancelled) {
            cancel()
        }
    }
}


suspend fun <T : Any> Call<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { continuation: CancellableContinuation<Result<T>> ->
    enqueue(object : Callback<T> {
        override fun onFailure(call: Call<T>?, t: Throwable) {
            if (continuation.isCancelled) return
            continuation.resume(Result.Exception(t))
        }

        override fun onResponse(call: Call<T>?, response: retrofit2.Response<T>) = when {
            response.isSuccessful -> {
                val body: T? = response.body()
                when {
                    body != null -> Result.Success(body, response.raw())
                    else -> Result.Exception(NullPointerException("Reponse body is null"))
                }
            }
            else -> {
                val errorBody = response.errorBody()
                when {
                    errorBody != null -> Result.Error(errorBody)
                    else -> Result.Exception(IllegalStateException("Error body is null"))
                }
            }
        }.let(continuation::resume)
    })

    registerOnComletion(continuation)
}

suspend fun getCurrentAccount(): Account = suspendCoroutine { continuation: Continuation<Account> ->
    AccountKit.getCurrentAccount(object : AccountKitCallback<Account> {
        override fun onSuccess(accout: Account) = continuation.resume(accout)

        override fun onError(error: AccountKitError) = continuation.resumeWithException(Exception(error.errorType.message))
    })
}

inline fun <reified T : Any> Retrofit.parse(responseBody: ResponseBody): T = responseBody.use {
    responseBodyConverter<T>(T::class.java, T::class.java.annotations)
            .convert(it)
}