package com.apexos.repoguardian.data.huggingface

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

// Base URL: https://huggingface.co/
interface HuggingFaceApi {
    @GET("api/models")
    suspend fun searchModels(
        @Query("search") search: String,
        @Query("filter") filter: String = "gguf",
        @Query("sort") sort: String = "downloads",
        @Query("direction") direction: String = "-1",
        @Query("limit") limit: Int = 20
    ): List<HfModelSearchResult>

    @GET("api/models/{modelId}")
    suspend fun getModelInfo(
        @Path("modelId", encoded = true) modelId: String
    ): HfModelSearchResult

    @GET("api/models/{modelId}/tree/main")
    suspend fun listModelFiles(
        @Path("modelId", encoded = true) modelId: String
    ): List<HfModelFile>

    @Streaming
    @GET
    suspend fun downloadFile(@Url fileUrl: String): ResponseBody
}
