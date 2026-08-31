package ru.slotelly.app.data

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import ru.slotelly.app.BuildConfig
import kotlin.jvm.JvmSuppressWildcards

interface SlotellyApi {
    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_api")
    suspend fun call(@Body body: ApiEnvelope): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_waitlist")
    suspend fun waitlist(@Body body: Map<String, String>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_waitlist_all")
    suspend fun waitlistAll(@Body body: Map<String, String>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_waitlist_detail")
    suspend fun waitlistDetail(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_waitlist_update")
    suspend fun waitlistUpdate(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_client_extra")
    suspend fun clientExtra(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_update_client")
    suspend fun updateClient(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/master_telegram_link_status")
    suspend fun telegramLinkStatus(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/master_create_telegram_link")
    suspend fun createTelegramLink(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_settings_patch")
    suspend fun settingsPatch(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_set_online_booking")
    suspend fun setOnlineBooking(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("functions/v1/resnitsy-notifications")
    suspend fun notifications(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    @Headers("Content-Type: application/json")
    @POST("functions/v1/resnitsy-dayoff")
    suspend fun dayoff(@Body body: Map<String, @JvmSuppressWildcards Any>): JsonObject

    companion object {
        fun create(): SlotellyApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("apikey", BuildConfig.SUPABASE_KEY)
                            .header("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
                            .build()
                    )
                }
                .build()
            return retrofit2.Retrofit.Builder()
                .baseUrl(BuildConfig.SUPABASE_URL + "/")
                .client(client)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(SlotellyApi::class.java)
        }
    }
}
