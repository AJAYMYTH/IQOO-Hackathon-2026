package com.apexos.repoguardian.data.github

import android.util.Base64
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
    suspend fun listRepos(): ApiResult<List<Repo>> = apiCall {
        dataApi.listRepos()
    }

    suspend fun listCommits(owner: String, repo: String): ApiResult<List<Commit>> = apiCall {
        dataApi.listCommits(owner, repo)
    }

    suspend fun getCommitDiff(owner: String, repo: String, sha: String): ApiResult<CommitDiffResponse> = apiCall {
        dataApi.getCommitDiff(owner, repo, sha)
    }

    suspend fun createFixPr(
        owner: String,
        repo: String,
        baseSha: String,
        filePath: String,
        fixedContent: String,
        commitMessage: String,
        prTitle: String,
        prBody: String
    ): ApiResult<PullRequest> = apiCall {
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
        val currentFile = dataApi.getFileContent(owner, repo, filePath, branchName)

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
                sha = currentFile.sha,
                branch = branchName
            )
        )

        // 4. Create PR
        dataApi.createPullRequest(
            owner, repo,
            CreatePrRequest(
                title = prTitle,
                head = branchName,
                base = "main",
                body = prBody
            )
        )
    }

    suspend fun getCheckRuns(owner: String, repo: String, ref: String): ApiResult<CheckRunsResponse> = apiCall {
        dataApi.getCheckRuns(owner, repo, ref)
    }

    suspend fun commitWorkflowFile(
        owner: String,
        repo: String,
        yamlContent: String,
        workflowName: String
    ): ApiResult<FileCommitResponse> = apiCall {
        val path = ".github/workflows/$workflowName.yml"
        val encodedContent = Base64.encodeToString(
            yamlContent.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        // Try to get existing file SHA (may not exist)
        val existingSha = try {
            dataApi.getFileContent(owner, repo, path).sha
        } catch (e: Exception) {
            "" // File doesn't exist yet
        }

        if (existingSha.isNotEmpty()) {
            dataApi.updateFile(
                owner, repo, path,
                UpdateFileRequest(
                    message = "Add CI/CD workflow: $workflowName (via Repo Guardian)",
                    content = encodedContent,
                    sha = existingSha,
                    branch = "main"
                )
            )
        } else {
            // For new files, we need to create via the contents API without SHA
            dataApi.updateFile(
                owner, repo, path,
                UpdateFileRequest(
                    message = "Add CI/CD workflow: $workflowName (via Repo Guardian)",
                    content = encodedContent,
                    sha = "",
                    branch = "main"
                )
            )
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> {
        return try {
            ApiResult.Success(block())
        } catch (e: retrofit2.HttpException) {
            ApiResult.Error(
                message = e.response()?.errorBody()?.string() ?: e.message(),
                code = e.code()
            )
        } catch (e: Exception) {
            ApiResult.Error(message = e.message ?: "Unknown error")
        }
    }
}
