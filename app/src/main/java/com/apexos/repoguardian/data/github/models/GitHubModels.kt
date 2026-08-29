package com.apexos.repoguardian.data.github.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// === Auth Models ===

@JsonClass(generateAdapter = true)
data class DeviceCodeRequest(
    @Json(name = "client_id") val clientId: String,
    val scope: String = "repo"
)

@JsonClass(generateAdapter = true)
data class DeviceCodeResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_uri") val verificationUri: String,
    @Json(name = "verification_uri_complete") val verificationUriComplete: String? = null,
    @Json(name = "expires_in") val expiresIn: Int,
    val interval: Int
)

@JsonClass(generateAdapter = true)
data class AccessTokenRequest(
    @Json(name = "client_id") val clientId: String,
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code"
)

@JsonClass(generateAdapter = true)
data class AccessTokenResponse(
    @Json(name = "access_token") val accessToken: String? = null,
    val error: String? = null,
    @Json(name = "error_description") val errorDescription: String? = null
)

// === User Models ===

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @Json(name = "public_repos") val publicRepos: Int = 0
)

// === Repo Models ===

@JsonClass(generateAdapter = true)
data class Repo(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    val private: Boolean = false,
    @Json(name = "html_url") val htmlUrl: String,
    val owner: RepoOwner
)

@JsonClass(generateAdapter = true)
data class RepoOwner(
    val login: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

// === Commit Models ===

@JsonClass(generateAdapter = true)
data class Commit(
    val sha: String,
    val commit: CommitDetail,
    @Json(name = "html_url") val htmlUrl: String? = null,
    val author: RepoOwner? = null
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    val message: String,
    val author: CommitAuthor? = null
)

@JsonClass(generateAdapter = true)
data class CommitAuthor(
    val name: String,
    val email: String? = null,
    val date: String? = null
)

@JsonClass(generateAdapter = true)
data class CommitDiffResponse(
    val sha: String,
    val commit: CommitDetail,
    val files: List<CommitFile>? = null
)

@JsonClass(generateAdapter = true)
data class CommitFile(
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
    @Json(name = "raw_url") val rawUrl: String? = null
)

// === PR Models ===

@JsonClass(generateAdapter = true)
data class CreatePrRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null
)

@JsonClass(generateAdapter = true)
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    @Json(name = "html_url") val htmlUrl: String,
    val head: PrBranch? = null,
    val base: PrBranch? = null
)

@JsonClass(generateAdapter = true)
data class PrBranch(
    val ref: String,
    val sha: String
)

// === Check Run Models ===

@JsonClass(generateAdapter = true)
data class CheckRunsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "check_runs") val checkRuns: List<CheckRun>
)

@JsonClass(generateAdapter = true)
data class CheckRun(
    val id: Long,
    val name: String,
    val status: String, // queued, in_progress, completed
    val conclusion: String? = null, // success, failure, neutral, etc.
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "started_at") val startedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null
)

// === Git Ref / Branch Models ===

@JsonClass(generateAdapter = true)
data class CreateRefRequest(
    val ref: String, // "refs/heads/branch-name"
    val sha: String
)

@JsonClass(generateAdapter = true)
data class GitRef(
    val ref: String,
    @Json(name = "object") val obj: GitObject? = null
)

@JsonClass(generateAdapter = true)
data class GitObject(
    val sha: String,
    val type: String? = null
)

// === File Content Update Models ===

@JsonClass(generateAdapter = true)
data class UpdateFileRequest(
    val message: String,
    val content: String, // Base64-encoded
    val sha: String, // SHA of the file being replaced
    val branch: String
)

@JsonClass(generateAdapter = true)
data class FileCommitResponse(
    val content: FileContent? = null,
    val commit: FileCommit? = null
)

@JsonClass(generateAdapter = true)
data class FileContent(
    val name: String? = null,
    val path: String? = null,
    val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class FileCommit(
    val sha: String? = null,
    val message: String? = null
)

// === File Content Read Model ===

@JsonClass(generateAdapter = true)
data class FileContentResponse(
    val name: String,
    val path: String,
    val sha: String,
    val content: String? = null, // Base64-encoded
    val encoding: String? = null
)
