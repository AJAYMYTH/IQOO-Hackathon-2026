package com.apexos.repoguardian.data.github.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// === Auth Models ===

@JsonClass(generateAdapter = true)
data class DeviceCodeRequest(
    @Json(name = "client_id") val clientId: String,
    val scope: String = "repo,workflow"
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

@JsonClass(generateAdapter = true)
data class RepoDetail(
    val id: Long,
    val name: String,
    @Json(name = "full_name") val fullName: String,
    val description: String? = null,
    val language: String? = null,
    @Json(name = "default_branch") val defaultBranch: String = "main",
    @Json(name = "stargazers_count") val stargazersCount: Int = 0,
    @Json(name = "forks_count") val forksCount: Int = 0,
    @Json(name = "open_issues_count") val openIssuesCount: Int = 0,
    val private: Boolean = false,
    val topics: List<String>? = null,
    @Json(name = "html_url") val htmlUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class DirectoryItem(
    val name: String,
    val path: String,
    val type: String, // "file" or "dir"
    val size: Long? = 0L
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
    val sha: String? = null, // SHA of the file being replaced (null when creating new file)
    val branch: String? = null
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
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    val content: String? = null, // Base64-encoded
    val encoding: String? = null,
    val size: Long? = 0L,
    val type: String? = null
)

// === Git Tree Models ===

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    val sha: String = "",
    val tree: List<GitTreeItem> = emptyList(),
    val truncated: Boolean = false
)

@JsonClass(generateAdapter = true)
data class GitTreeItem(
    val path: String = "",
    val mode: String? = null,
    val type: String = "blob", // "blob" (file) or "tree" (dir)
    val sha: String? = null,
    val size: Long? = null,
    val url: String? = null
)

// === Issue Models ===

@JsonClass(generateAdapter = true)
data class GitHubIssue(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    val user: RepoOwner? = null,
    @Json(name = "comments") val commentsCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

// === Release Models ===

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList()
) {
    val versionName: String get() = tagName.removePrefix("v").removePrefix("V")
    val apkAsset: ReleaseAsset? get() = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

@JsonClass(generateAdapter = true)
data class ReleaseAsset(
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = "",
    @Json(name = "content_type") val contentType: String? = null
) {
    val sizeFormatted: String get() {
        val mb = size.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }
}

