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
}
