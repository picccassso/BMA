package com.bma.android.api

import com.bma.android.models.Song
import retrofit2.http.*
import retrofit2.http.Streaming
import okhttp3.ResponseBody

interface BmaApi {
    @GET("health")
    suspend fun checkHealth(): Map<String, String>
    
    @GET("info")
    suspend fun getServerInfo(): Map<String, Any>
    
    @POST("pair")
    suspend fun requestPairing(): Map<String, String>
    
    @DELETE("pair/{token}")
    suspend fun revokePairing(@Path("token") token: String): ResponseBody
    
    @GET("songs")
    suspend fun getSongs(@Header("Authorization") authToken: String): List<Song>
    
    @GET("stream/{songId}")
    @Streaming
    suspend fun streamSong(
        @Path("songId") songId: String,
        @Header("Authorization") authToken: String
    ): ResponseBody
} 