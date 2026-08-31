package ru.slotelly.app.data

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import ru.slotelly.app.BuildConfig

interface SlotellyApi {
    @Headers("Content-Type: application/json")
    @POST("rest/v1/rpc/slotelly_mobile_api")
    suspend fun call(@Body body:ApiEnvelope):JsonObject
    companion object {
        fun create():SlotellyApi {
            val client=OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("apikey",BuildConfig.SUPABASE_KEY).header("Authorization","Bearer ${BuildConfig.SUPABASE_KEY}").build()) }.addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BASIC}).build()
            return retrofit2.Retrofit.Builder().baseUrl(BuildConfig.SUPABASE_URL+"/").client(client).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(SlotellyApi::class.java)
        }
    }
}
