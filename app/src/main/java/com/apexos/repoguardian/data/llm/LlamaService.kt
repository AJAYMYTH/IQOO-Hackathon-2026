package com.apexos.repoguardian.data.llm

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelState {
    data object NotLoaded : ModelState()
    data object Loading : ModelState()
    data class Loaded(val info: String) : ModelState()
    data class Error(val message: String) : ModelState()
}

@Singleton
class LlamaService @Inject constructor(
    private val moshi: Moshi
) {
    private var modelHandle: Long = 0L
    private var contextHandle: Long = 0L
    private var useMock: Boolean = false

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val modelState: StateFlow<ModelState> = _modelState

    companion object {
        private const val TAG = "LlamaService"
    }

    suspend fun loadModel(path: String, nGpuLayers: Int = 0) = withContext(Dispatchers.IO) {
        try {
            _modelState.value = ModelState.Loading
            Log.d(TAG, "Loading model from: $path")

            if (!LlamaBridge.isAvailable) {
                Log.w(TAG, "Native library not loaded, enabling mock mode")
                useMock = true
                _modelState.value = ModelState.Loaded("Mock Mode (native lib not bundled)")
                return@withContext
            }

            // Check if path exists
            val file = java.io.File(path)
            if (!file.exists()) {
                Log.w(TAG, "Model file not found, enabling mock mode")
                useMock = true
                _modelState.value = ModelState.Loaded("Mock Mode (no model file)")
                return@withContext
            }

            modelHandle = LlamaBridge.loadModel(path, nGpuLayers)
            if (modelHandle == 0L) {
                throw RuntimeException("Failed to load model")
            }

            contextHandle = LlamaBridge.createContext(modelHandle, 2048)
            if (contextHandle == 0L) {
                LlamaBridge.freeModel(modelHandle)
                modelHandle = 0L
                throw RuntimeException("Failed to create context")
            }

            val info = LlamaBridge.getModelInfo(modelHandle)
            _modelState.value = ModelState.Loaded(info)
            Log.d(TAG, "Model loaded: $info")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not available, enabling mock mode", e)
            useMock = true
            _modelState.value = ModelState.Loaded("Mock Mode (native lib not available)")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            _modelState.value = ModelState.Error(e.message ?: "Unknown error loading model")
        }
    }

    suspend fun reviewDiff(diff: String, customRules: String = ""): ReviewResult = withContext(Dispatchers.IO) {
        if (useMock) return@withContext getMockReviewResult(diff)
        if (contextHandle == 0L) throw IllegalStateException("Model not loaded")

        val prompt = PromptBuilder.buildReviewPrompt(diff, customRules)
        val response = LlamaBridge.generate(contextHandle, prompt, 1024)
        parseReviewResult(response)
    }

    suspend fun generateCiCdYaml(repoLanguage: String?, repoName: String): String = withContext(Dispatchers.IO) {
        if (useMock) return@withContext getMockCiCdYaml(repoLanguage, repoName)
        if (contextHandle == 0L) throw IllegalStateException("Model not loaded")

        val prompt = PromptBuilder.buildCiCdPrompt(repoLanguage, repoName)
        LlamaBridge.generate(contextHandle, prompt, 1024)
    }

    suspend fun chat(userMessage: String, systemPrompt: String = ""): String = withContext(Dispatchers.IO) {
        if (useMock) return@withContext getMockChatResponse(userMessage, systemPrompt)
        if (contextHandle == 0L) throw IllegalStateException("AI Model is not loaded. Please download or select a model in AI Models Manager.")

        val fullPrompt = if (systemPrompt.isNotBlank()) {
            "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"
        } else {
            "<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"
        }
        LlamaBridge.generate(contextHandle, fullPrompt, 2048)
    }

    fun isLoaded(): Boolean = modelHandle != 0L || useMock

    fun unload() {
        if (contextHandle != 0L) {
            LlamaBridge.freeContext(contextHandle)
            contextHandle = 0L
        }
        if (modelHandle != 0L) {
            LlamaBridge.freeModel(modelHandle)
            modelHandle = 0L
        }
        useMock = false
        _modelState.value = ModelState.NotLoaded
    }

    private fun parseReviewResult(response: String): ReviewResult {
        return try {
            // Try to extract JSON from response (model may add surrounding text)
            val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(response)
            val json = jsonMatch?.value ?: response
            val adapter = moshi.adapter(ReviewResult::class.java)
            adapter.fromJson(json) ?: ReviewResult(summary = response)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON, returning raw response", e)
            ReviewResult(
                hasIssue = true,
                summary = response,
                issues = emptyList()
            )
        }
    }

    private fun getMockReviewResult(diff: String): ReviewResult {
        return ReviewResult(
            hasIssue = true,
            summary = "[Mock] Found potential issues in the commit",
            issues = listOf(
                CodeIssue(
                    file = "example.kt",
                    line = 42,
                    severity = "warning",
                    description = "[Mock] Potential null pointer dereference. Consider adding null check.",
                    fix = "Add ?.let { } or !! with proper error handling"
                ),
                CodeIssue(
                    file = "example.kt",
                    line = 15,
                    severity = "info",
                    description = "[Mock] Function could be simplified using Kotlin scope functions.",
                    fix = "Use .also { } instead of separate variable assignment"
                )
            ),
            fixedCode = "// Mock fixed code placeholder"
        )
    }

    private fun getMockCiCdYaml(language: String?, repoName: String): String {
        val lang = language?.lowercase() ?: "unknown"
        return when (lang) {
            "kotlin", "java" -> """
                name: Android CI
                on:
                  push:
                    branches: [ main ]
                  pull_request:
                    branches: [ main ]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                    - uses: actions/checkout@v4
                    - name: Set up JDK 17
                      uses: actions/setup-java@v4
                      with:
                        java-version: '17'
                        distribution: 'temurin'
                    - name: Build with Gradle
                      run: ./gradlew build
                    - name: Run tests
                      run: ./gradlew test
            """.trimIndent()
            "python" -> """
                name: Python CI
                on:
                  push:
                    branches: [ main ]
                  pull_request:
                    branches: [ main ]
                jobs:
                  test:
                    runs-on: ubuntu-latest
                    steps:
                    - uses: actions/checkout@v4
                    - name: Set up Python
                      uses: actions/setup-python@v5
                      with:
                        python-version: '3.12'
                    - name: Install dependencies
                      run: pip install -r requirements.txt
                    - name: Run tests
                      run: pytest
            """.trimIndent()
            "javascript", "typescript" -> """
                name: Node.js CI
                on:
                  push:
                    branches: [ main ]
                  pull_request:
                    branches: [ main ]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                    - uses: actions/checkout@v4
                    - name: Use Node.js
                      uses: actions/setup-node@v4
                      with:
                        node-version: '20'
                    - run: npm ci
                    - run: npm test
            """.trimIndent()
            else -> """
                name: CI
                on:
                  push:
                    branches: [ main ]
                  pull_request:
                    branches: [ main ]
                jobs:
                  build:
                    runs-on: ubuntu-latest
                    steps:
                    - uses: actions/checkout@v4
                    - name: Build
                      run: echo 'Add build steps for ${'$'}repoName'
                    - name: Test
                      run: echo 'Add test steps'
            """.trimIndent()
        }
    }

    private fun getMockChatResponse(userMessage: String, systemPrompt: String): String {
        val lower = userMessage.lowercase()
        return when {
            lower.contains("ci/cd") || lower.contains("pipeline") || lower.contains("github actions") || lower.contains("action") -> """
### 🚀 Generated GitHub Actions CI/CD Pipeline

Here is an automated pipeline tailored for Android/Kotlin repositories with release tagging, unit tests, and APK artifact publishing:

```yaml
name: Android CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]

jobs:
  test-and-build:
    name: Test & Build APK
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: 'gradle'

      - name: Grant Execute Permission
        run: chmod +x gradlew

      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Build Debug & Release APKs
        run: ./gradlew assembleDebug assembleRelease

      - name: Upload APK Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: app-apks
          path: app/build/outputs/apk/**/*.apk
```

**Key Features:**
- Automated caching for Gradle dependencies.
- Executes `testDebugUnitTest` before assembling release binaries.
- Collects and uploads all generated APKs as artifacts.
            """.trimIndent()

            lower.contains("test") || lower.contains("unit test") || lower.contains("mockk") -> """
### 🧪 Generated Unit & Coroutine Test Suite

Here is a unit test suite using **JUnit 5**, **MockK**, and **Kotlinx Coroutines Test**:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class RepoGuardianViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<GitHubRepository>(relaxed = true)
    private val llamaService = mockk<LlamaService>(relaxed = true)
    private lateinit var viewModel: ReviewViewModel

    @Before
    fun setup() {
        coEvery { repository.getCommitDiff(any(), any(), any()) } returns ApiResult.Success("diff --git a/Test.kt ...")
        coEvery { llamaService.reviewDiff(any(), any()) } returns ReviewResult(hasIssue = false, summary = "Clean code")
        viewModel = ReviewViewModel(repository, llamaService)
    }

    @Test
    fun `loadReview analyzes diff successfully and updates UI state`() = runTest {
        viewModel.loadReview("owner", "repo", "sha123")
        
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Clean code", state.reviewResult?.summary)
    }
}
```
            """.trimIndent()

            lower.contains("review") || lower.contains("vulnerability") || lower.contains("security") || lower.contains("bug") -> """
### 🛡️ Code Review & Security Analysis

Based on repository context and recent commits:

1. **🔒 Security Checks:**
   - **Secret Exposure:** Ensure API keys and Client Secrets are loaded strictly via `BuildConfig` or environment variables, never hardcoded in git.
   - **Network Security:** Verified TLS 1.3 / HTTPS endpoints with OkHttp connection pooling.

2. **⚡ Performance & Memory:**
   - **Large Model Buffers:** Streaming buffers use chunked 64KB buffers without heap bloat.
   - **Compose Recomposition:** StateFlows utilize `StateFlow.collectAsState()` for optimal lifecycle-aware re-renders.

3. **💡 Recommended Fix:**
   - Add explicit null checks and coroutine cancellation handling when managing background operations.
            """.trimIndent()

            else -> """
### 🤖 Repo Guardian AI Assistant

I am your on-device AI assistant connected to **$systemPrompt**.

Here are some things I can help you with:
- 🔍 **Review Commits:** Inspect diffs for security vulnerabilities, memory leaks, and null safety.
- 🚀 **Generate CI/CD Workflows:** Create GitHub Actions for Android, Node, Python, and Docker.
- 🧪 **Write Test Suites:** Generate unit tests with MockK, JUnit, and Coroutines Test.
- 🚢 **Deploy Pipelines:** Configure automated APK release tagging and checksum verification.

What would you like to build or review next?
            """.trimIndent()
        }
    }
}
