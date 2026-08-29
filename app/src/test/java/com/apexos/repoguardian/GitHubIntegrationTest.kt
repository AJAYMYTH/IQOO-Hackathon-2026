package com.apexos.repoguardian

import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubDataApi
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.*
import com.apexos.repoguardian.data.llm.PromptBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GitHubIntegrationTest {

    // Mock implementation of GitHubDataApi simulating real GitHub REST API endpoints
    private class FakeGitHubDataApi : GitHubDataApi {
        override suspend fun getUser(): GitHubUser =
            GitHubUser(login = "octocat", name = "The Octocat", publicRepos = 42)

        override suspend fun listRepos(perPage: Int, sort: String, page: Int): List<Repo> =
            listOf(
                Repo(
                    id = 1L,
                    name = "Repo-Guardian",
                    fullName = "octocat/Repo-Guardian",
                    description = "On-device AI code assistant",
                    language = "Kotlin",
                    htmlUrl = "https://github.com/octocat/Repo-Guardian",
                    owner = RepoOwner(login = "octocat")
                )
            )

        override suspend fun getRepo(owner: String, repo: String): RepoDetail =
            RepoDetail(
                id = 1L,
                name = repo,
                fullName = "$owner/$repo",
                description = "On-device AI code reviewer and assistant",
                language = "Kotlin",
                defaultBranch = "main",
                stargazersCount = 120,
                forksCount = 15,
                openIssuesCount = 3
            )

        override suspend fun getReadme(owner: String, repo: String): FileContentResponse {
            val readmeText = "# Repo Guardian\nOn-device AI code assistant powered by LLaMA and GitHub REST API."
            val encoded = java.util.Base64.getEncoder().encodeToString(readmeText.toByteArray(Charsets.UTF_8))
            return FileContentResponse(
                name = "README.md",
                path = "README.md",
                sha = "abc123readme",
                content = encoded,
                encoding = "base64"
            )
        }

        override suspend fun getRootContents(owner: String, repo: String): List<DirectoryItem> =
            listOf(
                DirectoryItem(name = "app", path = "app", type = "dir"),
                DirectoryItem(name = "build.gradle.kts", path = "build.gradle.kts", type = "file", size = 1024L),
                DirectoryItem(name = "README.md", path = "README.md", type = "file", size = 512L)
            )

        override suspend fun getDirectoryContents(owner: String, repo: String, path: String, ref: String?): List<DirectoryItem> =
            listOf(
                DirectoryItem(name = "MainActivity.kt", path = "$path/MainActivity.kt", type = "file")
            )

        override suspend fun getGitTree(owner: String, repo: String, treeSha: String, recursive: Int): GitTreeResponse =
            GitTreeResponse(
                sha = "tree-sha-root",
                tree = listOf(
                    GitTreeItem(path = "app/src/main/java/MainActivity.kt", type = "blob", size = 1200L),
                    GitTreeItem(path = "app/src/main/java/GitHubAuthManager.kt", type = "blob", size = 2500L),
                    GitTreeItem(path = "app/src/main/java/LlamaService.kt", type = "blob", size = 4200L),
                    GitTreeItem(path = "build.gradle.kts", type = "blob", size = 1024L),
                    GitTreeItem(path = "README.md", type = "blob", size = 512L)
                )
            )

        override suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): FileContentResponse {
            val code = when (path) {
                "app/src/main/java/MainActivity.kt" -> "package com.apexos\nclass MainActivity : ComponentActivity() {\n    override fun onCreate() { /* real code */ }\n}"
                "build.gradle.kts" -> "plugins { id(\"com.android.application\") }\nandroid { compileSdk = 35 }"
                else -> "println(\"Hello from $path\")"
            }
            val encoded = java.util.Base64.getEncoder().encodeToString(code.toByteArray(Charsets.UTF_8))
            return FileContentResponse(
                name = path.substringAfterLast('/'),
                path = path,
                sha = "sha-$path",
                content = encoded,
                encoding = "base64"
            )
        }

        override suspend fun listCommits(owner: String, repo: String, perPage: Int): List<Commit> =
            listOf(
                Commit(
                    sha = "c0ffee1234567890",
                    commit = CommitDetail(
                        message = "feat: implement real GitHub REST API integration",
                        author = CommitAuthor(name = "Developer", email = "dev@example.com", date = "2026-08-29T12:00:00Z")
                    )
                )
            )

        override suspend fun getCommitDiff(owner: String, repo: String, sha: String): CommitDiffResponse =
            CommitDiffResponse(
                sha = sha,
                commit = CommitDetail(message = "feat: implement real GitHub REST API integration"),
                files = listOf(
                    CommitFile(
                        filename = "app/src/main/java/ChatViewModel.kt",
                        status = "modified",
                        additions = 50,
                        deletions = 10,
                        patch = "@@ -1,5 +1,10 @@\n+import com.apexos.repoguardian.data.github.GitHubRepository\n+val realData = gitHubRepository.getFileText(owner, repo, path)"
                    )
                )
            )

        override suspend fun listPulls(owner: String, repo: String, state: String, perPage: Int): List<PullRequest> =
            listOf(
                PullRequest(
                    id = 101L,
                    number = 12,
                    title = "Add real GitHub API context to on-device AI",
                    state = "open",
                    htmlUrl = "https://github.com/$owner/$repo/pull/12",
                    head = PrBranch(ref = "feature/real-github-data", sha = "abc1"),
                    base = PrBranch(ref = "main", sha = "main1")
                )
            )

        override suspend fun listIssues(owner: String, repo: String, state: String, perPage: Int): List<GitHubIssue> =
            listOf(
                GitHubIssue(
                    id = 201L,
                    number = 5,
                    title = "AI context needs actual source code from selected repo",
                    state = "open",
                    body = "Currently AI does not receive file contents.",
                    htmlUrl = "https://github.com/$owner/$repo/issues/5",
                    user = RepoOwner(login = "octocat")
                )
            )

        override suspend fun createPullRequest(owner: String, repo: String, body: CreatePrRequest): PullRequest =
            PullRequest(id = 999L, number = 13, title = body.title, state = "open", htmlUrl = "https://github.com/$owner/$repo/pull/13")

        override suspend fun getPullRequest(owner: String, repo: String, pullNumber: Int): PullRequest =
            PullRequest(
                id = pullNumber.toLong(),
                number = pullNumber,
                title = "Fix PR #$pullNumber",
                state = "open",
                htmlUrl = "https://github.com/$owner/$repo/pull/$pullNumber",
                head = PrBranch(ref = "repoguardian/fix-branch", sha = "fix-head-sha"),
                base = PrBranch(ref = "main", sha = "main-sha")
            )

        override suspend fun getCheckRuns(owner: String, repo: String, ref: String): CheckRunsResponse =
            CheckRunsResponse(totalCount = 1, checkRuns = listOf(CheckRun(id = 1L, name = "ci-build", status = "completed", conclusion = "success")))

        override suspend fun createBranch(owner: String, repo: String, body: CreateRefRequest): GitRef =
            GitRef(ref = body.ref, obj = GitObject(sha = body.sha, type = "commit"))

        override suspend fun updateFile(owner: String, repo: String, path: String, body: UpdateFileRequest): FileCommitResponse =
            FileCommitResponse(content = FileContent(name = path, path = path, sha = "new-sha"))

        override suspend fun getLatestRelease(owner: String, repo: String): GitHubRelease =
            GitHubRelease(
                tagName = "v1.2.0",
                name = "Repo Guardian v1.2.0",
                body = "### What's New\n- On-device LLM inference speedups\n- Direct in-app GGUF download\n- Automated in-app update installer",
                htmlUrl = "https://github.com/$owner/$repo/releases/tag/v1.2.0",
                assets = listOf(
                    ReleaseAsset(
                        name = "RepoGuardian-v1.2.0.apk",
                        size = 38 * 1024 * 1024L,
                        browserDownloadUrl = "https://github.com/$owner/$repo/releases/download/v1.2.0/RepoGuardian-v1.2.0.apk",
                        contentType = "application/vnd.android.package-archive"
                    )
                )
            )
    }

    @Test
    fun testGitHubRepositoryRealDataRetrieval() = runBlocking {
        val fakeApi = FakeGitHubDataApi()
        val repository = GitHubRepository(fakeApi)

        // 1. Get Repo Details
        val repoRes = repository.getRepo("octocat", "Repo-Guardian")
        assertTrue("getRepo should succeed", repoRes is ApiResult.Success)
        val repoData = (repoRes as ApiResult.Success).data
        assertEquals("Repo-Guardian", repoData.name)
        assertEquals("Kotlin", repoData.language)
        assertEquals("main", repoData.defaultBranch)
        assertEquals(120, repoData.stargazersCount)

        // 2. Get Real File Tree
        val treeRes = repository.getGitTree("octocat", "Repo-Guardian", "main", true)
        assertTrue("getGitTree should succeed", treeRes is ApiResult.Success)
        val tree = (treeRes as ApiResult.Success).data.tree
        assertEquals(5, tree.size)
        assertTrue(tree.any { it.path == "app/src/main/java/MainActivity.kt" })
        assertTrue(tree.any { it.path == "build.gradle.kts" })

        // 3. Get Real File Content
        val fileRes = repository.getFileText("octocat", "Repo-Guardian", "app/src/main/java/MainActivity.kt")
        assertTrue("getFileText should succeed", fileRes is ApiResult.Success)
        val fileContent = (fileRes as ApiResult.Success).data
        assertTrue("File content should contain actual source code", fileContent.contains("class MainActivity"))

        // 4. Get Real README
        val readmeRes = repository.getReadme("octocat", "Repo-Guardian")
        assertTrue("getReadme should succeed", readmeRes is ApiResult.Success)
        assertTrue((readmeRes as ApiResult.Success).data.contains("# Repo Guardian"))

        // 5. Get Real Commits & Diffs
        val commitsRes = repository.listCommits("octocat", "Repo-Guardian", 10)
        assertTrue("listCommits should succeed", commitsRes is ApiResult.Success)
        val commits = (commitsRes as ApiResult.Success).data
        assertEquals("c0ffee1234567890", commits.first().sha)

        val diffRes = repository.getCommitDiff("octocat", "Repo-Guardian", commits.first().sha)
        assertTrue("getCommitDiff should succeed", diffRes is ApiResult.Success)
        val diff = (diffRes as ApiResult.Success).data
        assertEquals(1, diff.files?.size)
        assertTrue(diff.files?.first()?.patch?.contains("gitHubRepository.getFileText") == true)

        // 6. Get Real Issues & PRs
        val issuesRes = repository.listIssues("octocat", "Repo-Guardian")
        assertTrue("listIssues should succeed", issuesRes is ApiResult.Success)
        assertEquals(5, (issuesRes as ApiResult.Success).data.first().number)

        val pullsRes = repository.listPulls("octocat", "Repo-Guardian")
        assertTrue("listPulls should succeed", pullsRes is ApiResult.Success)
        assertEquals(12, (pullsRes as ApiResult.Success).data.first().number)
    }

    @Test
    fun testPromptBuilderChatPromptWithRealContext() {
        val systemContext = """
Active Repository: octocat/Repo-Guardian
Description: On-device AI code reviewer
Language: Kotlin
Default Branch: main

Repository File Tree (5 total files indexed):
  - app/src/main/java/MainActivity.kt
  - app/src/main/java/LlamaService.kt
  - build.gradle.kts

=== [RETRIEVED REAL GITHUB SOURCE CODE] ===
#### Source File: `app/src/main/java/MainActivity.kt`
```kotlin
class MainActivity : ComponentActivity() { /* real code */ }
```
        """.trimIndent()

        val prompt = PromptBuilder.buildChatPrompt(
            userMessage = "What does MainActivity do?",
            systemContext = systemContext,
            isThinkingModel = true
        )

        assertTrue(prompt.contains("<|im_start|>system"))
        assertTrue(prompt.contains("Active Repository: octocat/Repo-Guardian"))
        assertTrue(prompt.contains("class MainActivity : ComponentActivity()"))
        assertTrue(prompt.contains("<think>...</think>"))
        assertTrue(prompt.contains("<|im_start|>user"))
        assertTrue(prompt.contains("What does MainActivity do?"))
        assertTrue(prompt.contains("<|im_start|>assistant"))
    }


    @Test
    fun testPromptBuilderCiCdPromptWithRealBuildManifest() {
        val buildManifest = "File: build.gradle.kts\n```\nplugins { id(\"com.android.application\") }\nandroid { compileSdk = 35 }\n```"
        val prompt = PromptBuilder.buildCiCdPrompt(
            language = "Kotlin",
            repoName = "Repo-Guardian",
            buildManifestContext = buildManifest
        )

        assertTrue(prompt.contains("Generate a production-ready GitHub Actions workflow"))
        assertTrue(prompt.contains("Actual repository build configuration"))
        assertTrue(prompt.contains("plugins { id(\"com.android.application\") }"))
        assertTrue(prompt.contains("Repo-Guardian"))
    }

    @Test
    fun testPromptBuilderReviewPromptWithRealDiff() {
        val diff = "--- a/MainActivity.kt\n+++ b/MainActivity.kt\n@@ -1,3 +1,3 @@\n-val x = null\n+val x = \"safe\""
        val repoContext = "Repository: octocat/Repo-Guardian | Target Commit SHA: c0ffee1"
        val prompt = PromptBuilder.buildReviewPrompt(
            diff = diff,
            customRules = "Enforce null safety",
            repoContext = repoContext
        )

        assertTrue(prompt.contains("Analyze the real git diff and source code"))
        assertTrue(prompt.contains("Repository: octocat/Repo-Guardian"))
        assertTrue(prompt.contains("Enforce null safety"))
        assertTrue(prompt.contains("+val x = \"safe\""))
    }

    @Test
    fun testDynamicSourceExtractionForSpecificFileQuery() = runBlocking {
        val fakeApi = FakeGitHubDataApi()
        val repository = GitHubRepository(fakeApi)

        val owner = "octocat"
        val repo = "Repo-Guardian"

        // 1. Fetch file tree
        val treeRes = repository.getGitTree(owner, repo)
        assertTrue(treeRes is ApiResult.Success)
        val allFiles = (treeRes as ApiResult.Success).data.tree.map { it.path }

        // User asks about GitHubAuthManager.kt
        val query = "How is authentication handled in GitHubAuthManager.kt?"
        val matchedFiles = allFiles.filter { query.contains(it.substringAfterLast('/'), ignoreCase = true) }
        assertEquals(1, matchedFiles.size)
        assertEquals("app/src/main/java/GitHubAuthManager.kt", matchedFiles.first())

        // Fetch actual source
        val fileContentRes = repository.getFileText(owner, repo, matchedFiles.first())
        assertTrue(fileContentRes is ApiResult.Success)
        val code = (fileContentRes as ApiResult.Success).data
        assertTrue(code.contains("Hello from app/src/main/java/GitHubAuthManager.kt"))

        // Format into AI context
        val contextSnippet = "#### Source File: `${matchedFiles.first()}`\n```kotlin\n$code\n```"
        val fullPrompt = PromptBuilder.buildChatPrompt(query, contextSnippet, true)

        assertTrue(fullPrompt.contains("Source File: `app/src/main/java/GitHubAuthManager.kt`"))
        assertTrue(fullPrompt.contains("How is authentication handled in GitHubAuthManager.kt?"))
    }

    @Test
    fun testDynamicCommitDiffExtractionForReviewQuery() = runBlocking {
        val fakeApi = FakeGitHubDataApi()
        val repository = GitHubRepository(fakeApi)

        val owner = "octocat"
        val repo = "Repo-Guardian"

        // Fetch real commits
        val commitsRes = repository.listCommits(owner, repo, 5)
        assertTrue(commitsRes is ApiResult.Success)
        val latestCommit = (commitsRes as ApiResult.Success).data.first()

        // Fetch real commit diff
        val diffRes = repository.getCommitDiff(owner, repo, latestCommit.sha)
        assertTrue(diffRes is ApiResult.Success)
        val diff = (diffRes as ApiResult.Success).data

        val patch = diff.files?.first()?.patch ?: ""
        assertTrue(patch.contains("gitHubRepository.getFileText"))

        val diffContext = "### Commit Diff (${latestCommit.sha.take(7)}: ${latestCommit.commit.message})\n```diff\n$patch\n```"
        val fullPrompt = PromptBuilder.buildChatPrompt("Review the recent commits for logic errors", diffContext, true)

        assertTrue(fullPrompt.contains("gitHubRepository.getFileText"))
        assertTrue(fullPrompt.contains("Review the recent commits for logic errors"))
    }

    @Test
    fun testGitHubApiErrorHandlingWhenFileNotFound() = runBlocking {
        val failingApi = object : GitHubDataApi by FakeGitHubDataApi() {
            override suspend fun getFileContent(owner: String, repo: String, path: String, ref: String?): FileContentResponse {
                throw retrofit2.HttpException(
                    retrofit2.Response.error<FileContentResponse>(
                        404,
                        okhttp3.ResponseBody.create(null, "{\"message\": \"Not Found\"}")
                    )
                )
            }
        }

        val repository = GitHubRepository(failingApi)
        val result = repository.getFileText("octocat", "Repo-Guardian", "non_existent_file.kt")

        assertTrue("Result must be ApiResult.Error", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(404, error.code)
        assertTrue(error.message.contains("404"))
    }

    @Test
    fun testGitHubRepositoryGetLatestRelease() = runBlocking {
        val fakeApi = FakeGitHubDataApi()
        val repository = GitHubRepository(fakeApi)

        val result = repository.getLatestRelease("octocat", "Repo-Guardian")
        assertTrue("getLatestRelease must succeed", result is ApiResult.Success)

        val release = (result as ApiResult.Success).data
        assertEquals("v1.2.0", release.tagName)
        assertEquals("1.2.0", release.versionName)
        assertNotNull("apkAsset should be present", release.apkAsset)
        assertEquals("RepoGuardian-v1.2.0.apk", release.apkAsset?.name)
        assertTrue(release.apkAsset?.browserDownloadUrl?.endsWith(".apk") == true)
        assertEquals("38.0 MB", release.apkAsset?.sizeFormatted)
    }
}

