package com.bma.android.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object ApiClient {
    private var baseUrl: String = ""
    private var authToken: String? = null
    
    fun setServerUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
        resetClient()
    }
    
    fun getServerUrl(): String = baseUrl
    
    fun setAuthToken(token: String?) {
        authToken = token
    }
    
    fun getAuthToken(): String? = authToken
    
    fun getAuthHeader(): String? {
        return authToken?.let { "Bearer $it" }
    }
    
    fun isAuthenticated(): Boolean = authToken != null
    
    private var retrofit: Retrofit? = null
    
    private fun resetClient() {
        retrofit = null
    }
    
    private fun getClient(): Retrofit {
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            val clientBuilder = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
            
            // For HTTPS support, we need to handle both secure and insecure connections
            // In production, you'd want proper certificate validation
            if (baseUrl.startsWith("https://")) {
                try {
                    // Create a trust manager that accepts all certificates
                    // Note: This is for development/local use only
                    val trustAllCerts = arrayOf<TrustManager>(
                        object : X509TrustManager {
                            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                        }
                    )
                    
                    val sslContext = SSLContext.getInstance("SSL")
                    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                    
                    clientBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    clientBuilder.hostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    // Fall back to default SSL handling
                }
            }
            
            val client = clientBuilder.build()
            
            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }
    
    val api: BmaApi by lazy {
        getClient().create(BmaApi::class.java)
    }
} 