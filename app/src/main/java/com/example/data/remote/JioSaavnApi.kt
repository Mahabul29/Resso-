package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface JioSaavnApi {
    @GET("api/trending/songs")
    suspend fun getTrendingSongs(@Query("limit") limit: Int = 30): Response<ResponseBody>

    @GET("api/trending")
    suspend fun getTrendingFallback(@Query("limit") limit: Int = 30): Response<ResponseBody>

    @GET("api/search/songs")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Query("page") page: Int = 1
    ): Response<ResponseBody>

    @GET("api/search")
    suspend fun searchFallback(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
        @Query("page") page: Int = 1
    ): Response<ResponseBody>

    @GET("api/search/artists")
    suspend fun searchArtists(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): Response<ResponseBody>

    @GET("api/lyrics/{id}/sync")
    suspend fun getSyncedLyrics(@Path("id") id: String): Response<ResponseBody>

    @GET("api/lyrics/{id}")
    suspend fun getLyrics(@Path("id") id: String): Response<ResponseBody>

    /** Songs related to the given song (same endpoint family as saavn.dev). */
    @GET("api/songs/{id}/suggestions")
    suspend fun getSongSuggestions(
        @Path("id") id: String,
        @Query("limit") limit: Int = 25
    ): Response<ResponseBody>
}
