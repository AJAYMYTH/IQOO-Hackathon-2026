package com.apexos.repoguardian.data.github

import com.apexos.repoguardian.data.github.models.*
import retrofit2.http.*

// Auth API (base URL: https://github.com/)
interface GitHubAuthApi {
    @Headers("Accept: application/json")
    @POST("login/device/code")
    suspend fun requestDeviceCode(@Body body: DeviceCodeRequest): DeviceCodeResponse

    @Headers("Accept: application/json")
    @POST("login/oauth/access_token")
    suspend fun pollAccessToken(@Body body: AccessTokenRequest): AccessTokenResponse
}

// Data API (base URL: https://api.github.com/)
interface GitHubDataApi {
    @GET("user")
    suspend fun getUser(): GitHubUser

    @GET("user/repos")
    suspend fun listRepos(
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "updated",
        @Query("page") page: Int = 1
    ): List<Repo>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun listCommits(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): List<Commit>

    @GET("repos/{owner}/{repo}/commits/{sha}")
    suspend fun getCommitDiff(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String
    ): CommitDiffResponse

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreatePrRequest
    ): PullRequest

    @GET("repos/{owner}/{repo}/commits/{ref}/check-runs")
    suspend fun getCheckRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("ref") ref: String
    ): CheckRunsResponse

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: CreateRefRequest
    ): GitRef

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): FileContentResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun updateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: UpdateFileRequest
    ): FileCommitResponse
}
