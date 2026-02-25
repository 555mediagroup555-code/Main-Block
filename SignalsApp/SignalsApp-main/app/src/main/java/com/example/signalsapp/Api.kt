package com.example.signalsapp

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

data class LiteSignal(
    val symbol: String?,
    val signal: String?,
    val score: Int?,
    val timeframe: String?,
    val price: Double?,
    val entry_zone: List<Double>?,
    val stop_loss: Double?,
    val take_profit: List<Double>?,
    val summary: String?,
    @SerializedName(value = "entry_explanation", alternate = ["entry_reason", "recommended_entry_reason"])
    val entryExplanation: String? = null,
    @SerializedName(value = "recommended_entry", alternate = ["recommended_entry_price", "entry_price"])
    val recommendedEntryPrice: Double? = null,
    @SerializedName("daily_sell_volume")
    val dailySellVolume: Double? = null,
    @SerializedName("market_cap")
    val marketCap: Double? = null
)

interface SignalsApi {
    @GET("scan")
    suspend fun scanLite(): List<LiteSignal>

    @GET("scan")
    suspend fun scan(): List<LiteSignal>
}

fun buildApi(baseUrl: String): SignalsApi {
    val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    return retrofit.create(SignalsApi::class.java)
}
