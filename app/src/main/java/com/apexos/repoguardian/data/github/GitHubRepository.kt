package com.apexos.repoguardian.data.github

import android.util.Base64
import android.util.Log
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.models.*
import javax.inject.Inject
import javax.inject.Singleton


sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
}

@Singleton
class GitHubRepository @Inject constructor(
    private val dataApi: GitHubDataApi
) {
    companion object {
        private const val TAG = "GitHubRepository"
    }

    suspend fun getUser(): ApiResult<GitHubUser> = apiCall("getUser") {
        dataApi.getUser()
    }

    suspend fun listRepos(perPage: Int = 50): ApiResult<List<Repo>> = apiCall("listRepos(perPage=$perPage)") {
        dataApi.listRepos(perPage = perPage)
    }

    suspend fun getRepo(owner: String, repo: String): ApiResult<RepoDetail> = apiCall("getRepo($owner/$repo)") {
        dataApi.getRepo(owner, repo)
    }

    suspend fun getReadme(owner: String, repo: String): ApiResult<String> = apiCall("getReadme($owner/$repo)") {
        val res = dataApi.getReadme(owner, repo)
        decodeBase64ToString(res.content)
    }

    suspend fun getRootContents(owner: String, repo: String): ApiResult<List<DirectoryItem>> = apiCall("getRootContents($owner/$repo)") {
        dataApi.getRootContents(owner, repo)
    }

    suspend fun getDirectoryContents(owner: String, repo: String, path: String, ref: String? = null): ApiResult<List<DirectoryItem>> =
        apiCall("getDirectoryContents($owner/$repo, path=$path, ref=$ref)") {
            dataApi.getDirectoryContents(owner, repo, path, ref)
        }

    suspend fun getGitTree(owner: String, repo: String, treeSha: String = "HEAD", recursive: Boolean = true): ApiResult<GitTreeResponse> =
        apiCall("getGitTree($owner/$repo, ref=$treeSha, recursive=$recursive)") {
            dataApi.getGitTree(owner, repo, treeSha, if (recursive) 1 else 0)
        }

    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String? = null): ApiResult<FileContentResponse> =
        apiCall("getFileContent($owner/$repo, path=$path, ref=$ref)") {
            dataApi.getFileContent(owner, repo, path, ref)
        }

    suspend fun getFileText(owner: String, repo: String, path: String, ref: String? = null): ApiResult<String> =
        apiCall("getFileText($owner/$repo, path=$path, ref=$ref)") {
            val res = dataApi.getFileContent(owner, repo, path, ref)
            val decoded = decodeBase64ToString(res.content)
            if (decoded.isEmpty() && res.content != null && res.content.isNotBlank()) {
                Log.w(TAG, "Base64 decode yielded empty string for non-empty content in $path")
            }
            decoded
        }

    suspend fun listCommits(owner: String, repo: String, perPage: Int = 20): ApiResult<List<Commit>> =
        apiCall("listCommits($owner/$repo, perPage=$perPage)") {
            dataApi.listCommits(owner, repo, perPage)
        }

    suspend fun getCommitDiff(owner: String, repo: String, sha: String): ApiResult<CommitDiffResponse> =
        apiCall("getCommitDiff($owner/$repo, sha=$sha)") {
            dataApi.getCommitDiff(owner, repo, sha)
        }

    suspend fun listPulls(owner: String, repo: String, state: String = "open", perPage: Int = 10): ApiResult<List<PullRequest>> =
        apiCall("listPulls($owner/$repo, state=$state, perPage=$perPage)") {
            dataApi.listPulls(owner, repo, state, perPage)
        }

    suspend fun getPullRequest(owner: String, repo: String, pullNumber: Int): ApiResult<PullRequest> =
        apiCall("getPullRequest($owner/$repo, #$pullNumber)") {
            dataApi.getPullRequest(owner, repo, pullNumber)
        }

    suspend fun listIssues(owner: String, repo: String, state: String = "open", perPage: Int = 10): ApiResult<List<GitHubIssue>> =
        apiCall("listIssues($owner/$repo, state=$state, perPage=$perPage)") {
            dataApi.listIssues(owner, repo, state, perPage)
        }

    suspend fun createFixPr(
        owner: String,
        repo: String,
        baseSha: String,
        filePath: String,
        fixedContent: String,
        commitMessage: String,
        prTitle: String,
        prBody: String,
        baseBranch: String = "main"
    ): ApiResult<PullRequest> = apiCall("createFixPr($owner/$repo, file=$filePath)") {
        // 1. Create branch
        val branchName = "repoguardian/fix-${System.currentTimeMillis()}"
        dataApi.createBranch(
            owner, repo,
            CreateRefRequest(
                ref = "refs/heads/$branchName",
                sha = baseSha
            )
        )

        // 2. Get current file content to get its SHA
        val currentFile = try {
            dataApi.getFileContent(owner, repo, filePath, branchName)
        } catch (e: Exception) {
            null
        }

        // 3. Commit the fix
        val encodedContent = Base64.encodeToString(
            fixedContent.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )
        dataApi.updateFile(
            owner, repo, filePath,
            UpdateFileRequest(
                message = commitMessage,
                content = encodedContent,
                sha = currentFile?.sha,
                branch = branchName
            )
        )

        // 4. Create PR
        dataApi.createPullRequest(
            owner, repo,
            CreatePrRequest(
                title = prTitle,
                head = branchName,
                base = baseBranch,
                body = prBody
            )
        )
    }

    suspend fun getCheckRuns(owner: String, repo: String, ref: String): ApiResult<CheckRunsResponse> =
        apiCall("getCheckRuns($owner/$repo, ref=$ref)") {
            dataApi.getCheckRuns(owner, repo, ref)
        }

    suspend fun commitWorkflowFile(
        owner: String,
        repo: String,
        yamlContent: String,
        workflowName: String,
        branch: String? = null
    ): ApiResult<FileCommitResponse> = apiCall("commitWorkflowFile($owner/$repo, name=$workflowName)") {
        val path = ".github/workflows/$workflowName.yml"
        val encodedContent = Base64.encodeToString(
            yamlContent.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // Resolve target branch (use passed branch or query repo default_branch)
        val targetBranch = if (!branch.isNullOrBlank()) {
            branch
        } else {
            try {
                val repoDetail = dataApi.getRepo(owner, repo)
                if (repoDetail.defaultBranch.isNotBlank()) repoDetail.defaultBranch else null
            } catch (e: Exception) {
                null
            }
        }

        // Try to get existing file SHA (null if new file)
        val existingSha: String? = try {
            val fileRes = dataApi.getFileContent(owner, repo, path, ref = targetBranch)
            if (fileRes.sha.isNotBlank()) fileRes.sha else null
        } catch (e: Exception) {
            null
        }

        dataApi.updateFile(
            owner, repo, path,
            UpdateFileRequest(
                message = "Add CI/CD workflow: $workflowName (via Repo Guardian)",
                content = encodedContent,
                sha = existingSha,
                branch = targetBranch
            )
        )
    }

    suspend fun getLatestRelease(owner: String, repo: String): ApiResult<GitHubRelease> =
        apiCall("getLatestRelease ($owner/$repo)") {
            dataApi.getLatestRelease(owner, repo)
        }

    private fun decodeBase64ToString(rawContent: String?): String {
        if (rawContent.isNullOrBlank()) return ""
        val cleanBase64 = rawContent.replace("\n", "").replace("\r", "").replace(" ", "").trim()
        
        // 1. Try standard java.util.Base64 (native on minSdk 28+ and JVM)
        try {
            val bytes = java.util.Base64.getDecoder().decode(cleanBase64)
            val str = String(bytes, Charsets.UTF_8)
            if (str.isNotEmpty()) return str
        } catch (ignored: Throwable) {}

        // 2. Try android.util.Base64
        try {
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            if (bytes != null && bytes.isNotEmpty()) {
                val str = String(bytes, Charsets.UTF_8)
                if (str.isNotEmpty()) return str
            }
        } catch (ignored: Throwable) {}

        return ""
    }


    private suspend fun <T> apiCall(operation: String, block: suspend () -> T): ApiResult<T> {
        return try {
            AppLogger.d(TAG, "GitHub REST API Request: $operation")
            val result = block()
            AppLogger.i(TAG, "GitHub REST API Success: $operation")
            ApiResult.Success(result)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val detailedMsg = errorBody ?: e.message()
            AppLogger.e(TAG, "GitHub REST API HTTP ${e.code()} Error for $operation: $detailedMsg")
            val userMsg = if (e.code() == 404 && operation.contains("commitWorkflowFile")) {
                "GitHub 404: Missing 'workflow' OAuth permission or write access. To manage .github/workflows files, please log out and sign in again to grant workflow scope."
            } else {
                "GitHub API HTTP ${e.code()}: $detailedMsg"
            }
            ApiResult.Error(
                message = userMsg,
                code = e.code()
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "GitHub REST API Error for $operation: ${e.message}", e)
            ApiResult.Error(message = e.localizedMessage ?: e.message ?: "Unknown error")
        }
    }
}



