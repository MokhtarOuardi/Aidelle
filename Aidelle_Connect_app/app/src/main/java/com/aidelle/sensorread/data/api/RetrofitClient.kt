package com.aidelle.sensorread.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit client with configurable base URL.
 *
 * Default URL points to 10.0.2.2:8000 which maps to localhost
 * when running on Android Emulator. For physical devices, use
 * your computer's local IP address (e.g., 192.168.x.x:8000).
 */
object RetrofitClient {

    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000/"

    private var _baseUrl: String = DEFAULT_BASE_URL
    private var _retrofit: Retrofit? = null
    private var _apiService: ApiService? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Get or create the API service instance.
     */
    fun getApiService(): ApiService {
        if (_apiService == null) {
            _apiService = buildRetrofit().create(ApiService::class.java)
        }
        return _apiService!!
    }

    /**
     * Update the server base URL and rebuild the client.
     * Call this when the user changes the server address in settings.
     */
    fun setBaseUrl(url: String) {
        val normalizedUrl = if (url.endsWith("/")) url else "$url/"
        if (normalizedUrl != _baseUrl) {
            _baseUrl = normalizedUrl
            _retrofit = null
            _apiService = null
        }
    }

    /**
     * Get the current base URL.
     */
    fun getBaseUrl(): String = _baseUrl

    private fun buildRetrofit(): Retrofit {
        if (_retrofit == null) {
            _retrofit = Retrofit.Builder()
                .baseUrl(_baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return _retrofit!!
    }
}
