package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface JioSaavnApi {
    @GET("api/trending/songs")
    suspend fun getTrendingSongs(@Query("limit") limit: Int = 30): Response<ResponseBody>

    @GET("api/trending")
    suspend fun getTrendingFallback(@Query("limit") limit: Int = 30): Response<ResponseBody>

    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30
    ): Response<ResponseBody>

    @GET("api/search")
    suspend fun searchFallback(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30
    ): Response<ResponseBody>
}
