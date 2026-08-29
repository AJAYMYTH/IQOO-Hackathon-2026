package com.apexos.repoguardian.data.llm

import android.content.Context
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.preferences.PreferencesManager
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ModelState {
    object NotLoaded : ModelState()
    object Loading : ModelState()
    data class Loaded(val modelInfo: String) : ModelState()
    data class Error(val message: String) : ModelState()
}

@Singleton
class LlamaService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val preferencesManager: PreferencesManager
) {
    private var modelHandle: Long = 0L
    private var contextHandle: Long = 0L
    private val loadMutex = Mutex()
    private val inferenceMutex = Mutex()

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val modelState: StateFlow<ModelState> = _modelState

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "LlamaService"
    }

    suspend fun autoStartService() = withContext(Dispatchers.IO) {
        if (isLoaded()) return@withContext

        try {
            // 1. Check configured model path
            val modelPath = preferencesManager.getModelPath()
            if (!modelPath.isNullOrBlank() && File(modelPath).exists() && File(modelPath).length() > 0) {
                val backend = preferencesManager.getBackend()
                val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0
                AppLogger.i(TAG, "Auto-starting with saved model: $modelPath (layers: $gpuLayers)")
                loadModel(modelPath, gpuLayers)
                return@withContext
            }

            // 2. Auto-discover model in internal app storage
            val modelsDir = File(context.filesDir, "models")
            if (modelsDir.exists()) {
                val ggufFiles = modelsDir.listFiles()?.filter { it.extension.equals("gguf", ignoreCase = true) && it.length() > 0 } ?: emptyList()
                if (ggufFiles.isNotEmpty()) {
                    val best = ggufFiles.maxByOrNull { it.lastModified() } ?: ggufFiles.first()
                    preferencesManager.saveModelPath(best.absolutePath)
                    val backend = preferencesManager.getBackend()
                    val gpuLayers = if (backend == "gpu" || backend == "npu") 33 else 0
                    AppLogger.i(TAG, "Auto-discovered internal GGUF model: ${best.name}")
                    loadModel(best.absolutePath, gpuLayers)
                    return@withContext
                }
            }

            // 3. Auto-discover model in common Android Download paths (safely wrapped)
            try {
                val downloadDir = File("/sdcard/Download")
                if (downloadDir.exists() && downloadDir.canRead()) {
                    val sideLoaded = downloadDir.listFiles()?.filter { it.extension.equals("gguf", ignoreCase = true) && it.length() > 0 } ?: emptyList()
                    if (sideLoaded.isNotEmpty()) {
                        val first = sideLoaded.first()
                        preferencesManager.saveModelPath(first.absolutePath)
                        AppLogger.i(TAG, "Auto-discovered downloaded GGUF model: ${first.name}")
                        loadModel(first.absolutePath, 0)
                        return@withContext
                    }
                }
            } catch (e: Throwable) {
                AppLogger.d(TAG, "Scoped storage bypass: ${e.message}")
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Silent auto-start skipped: ${e.message}")
        }
    }

    suspend fun loadModel(path: String, nGpuLayers: Int = 0) = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            loadMutex.withLock {
                if (modelHandle != 0L && contextHandle != 0L) {
                    val currentLoadedPath = preferencesManager.getModelPath()
                    if (currentLoadedPath == path) {
                        AppLogger.d(TAG, "Model $path is already active in memory, reusing instance.")
                        return@withContext
                    }
                    unloadInternal()
                }

                try {
                    _modelState.value = ModelState.Loading
                    AppLogger.i(TAG, "Loading GGUF model: $path (GPU layers: $nGpuLayers)")

                    val file = File(path)
                    if (!file.exists()) {
                        val err = "Model file not found at path: $path"
                        AppLogger.e(TAG, err)
                        _modelState.value = ModelState.Error(err)
                        return@withContext
                    }

                    if (!LlamaBridge.isAvailable) {
                        val err = "LlamaBridge native library not available in runtime"
                        AppLogger.e(TAG, err)
                        _modelState.value = ModelState.Error(err)
                        return@withContext
                    }

                    modelHandle = LlamaBridge.loadModel(path, nGpuLayers)
                    if (modelHandle == 0L) {
                        val err = "LlamaBridge failed to load GGUF model: ${file.name}"
                        AppLogger.e(TAG, err)
                        _modelState.value = ModelState.Error(err)
                        return@withContext
                    }

                    contextHandle = LlamaBridge.createContext(modelHandle, 4096)
                    if (contextHandle == 0L) {
                        val err = "LlamaBridge failed to create inference context for ${file.name}"
                        AppLogger.e(TAG, err)
                        _modelState.value = ModelState.Error(err)
                        return@withContext
                    }

                    val info = LlamaBridge.getModelInfo(modelHandle)
                    val desc = if (info.isNotBlank() && !info.contains("No model")) info else "Active: ${file.name}"
                    _modelState.value = ModelState.Loaded(desc)
                    AppLogger.i(TAG, "GGUF model loaded successfully into memory: $desc")
                } catch (e: UnsatisfiedLinkError) {
                    val err = "Native bridge link error: ${e.message}"
                    AppLogger.e(TAG, err, e)
                    _modelState.value = ModelState.Error(err)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error loading model", e)
                    _modelState.value = ModelState.Error(e.message ?: "Unknown error loading model")
                }
            }
        }
    }

    suspend fun reviewDiff(diff: String, customRules: String = "", repoContext: String = ""): ReviewResult = withContext(Dispatchers.IO) {
        if (!isLoaded()) {
            autoStartService()
        }
        val startTime = System.currentTimeMillis()
        val prompt = PromptBuilder.buildReviewPrompt(diff, customRules, repoContext)
        val localServerUrl = preferencesManager.getLocalServerUrl().trim()
        val backendPref = preferencesManager.getBackend().lowercase()
        val activeBackend = when {
            localServerUrl.isNotBlank() -> "Local Server ($localServerUrl)"
            backendPref == "npu" -> "Snapdragon NPU (Hexagon)"
            backendPref == "gpu" -> "Adreno GPU (Vulkan)"
            else -> "CPU (ARM NEON)"
        }

        // 1. Try Local Server if configured
        if (localServerUrl.isNotBlank()) {
            AppLogger.i(TAG, "Querying local server for code review: $localServerUrl")
            val serverResp = queryLocalServer(prompt, 4096, localServerUrl)
            if (!serverResp.isNullOrBlank()) {
                val elapsedMs = System.currentTimeMillis() - startTime
                val approxTokens = serverResp.length / 4
                val tps = if (elapsedMs > 0) (approxTokens.toDouble() / (elapsedMs / 1000.0)) else 0.0
                val metrics = InferenceMetrics(
                    totalTimeMs = elapsedMs,
                    tokenCount = approxTokens,
                    tokensPerSecond = tps,
                    backend = activeBackend
                )
                AppLogger.i(TAG, "Received review response from local server (${serverResp.length} chars in ${elapsedMs}ms, ~${String.format("%.1f", tps)} tok/s)")
                return@withContext parseReviewResult(serverResp, metrics)
            }
        }

        // 2. Try On-Device Native LLM
        if (contextHandle != 0L) {
            inferenceMutex.withLock {
                try {
                    AppLogger.i(TAG, "Running code review via on-device native LLM (prompt: ${prompt.length} chars, backend: $activeBackend)...")
                    val response = LlamaBridge.generate(contextHandle, prompt, 4096)
                    if (response.isNotBlank() && !response.startsWith("Error:")) {
                        val elapsedMs = System.currentTimeMillis() - startTime
                        val approxTokens = response.length / 4
                        val tps = if (elapsedMs > 0) (approxTokens.toDouble() / (elapsedMs / 1000.0)) else 0.0
                        val metrics = InferenceMetrics(
                            totalTimeMs = elapsedMs,
                            tokenCount = approxTokens,
                            tokensPerSecond = tps,
                            backend = activeBackend
                        )
                        AppLogger.i(TAG, "On-device native LLM review completed successfully in ${elapsedMs}ms (~${String.format("%.1f", tps)} tok/s)")
                        return@withContext parseReviewResult(response, metrics)
                    } else {
                        AppLogger.w(TAG, "Native LLM returned error output: $response")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Native review generation failed", e)
                }
            }
        }

        throw IllegalStateException("No active GGUF model or responsive Local Server available to execute code review.")
    }

    suspend fun generateCiCdYaml(
        repoLanguage: String?,
        repoName: String,
        buildManifestContext: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isLoaded()) {
            autoStartService()
        }
        val prompt = PromptBuilder.buildCiCdPrompt(repoLanguage, repoName, buildManifestContext)
        val localServerUrl = preferencesManager.getLocalServerUrl().trim()

        // 1. Try Local Server if configured
        if (localServerUrl.isNotBlank()) {
            AppLogger.i(TAG, "Querying local server for CI/CD YAML: $localServerUrl")
            val serverResp = queryLocalServer(prompt, 4096, localServerUrl)
            if (!serverResp.isNullOrBlank()) {
                AppLogger.i(TAG, "Received CI/CD YAML from local server")
                return@withContext cleanYamlOutput(serverResp)
            }
        }

        // 2. Try On-Device Native LLM
        if (contextHandle != 0L) {
            inferenceMutex.withLock {
                try {
                    AppLogger.i(TAG, "Generating CI/CD workflow via on-device native LLM...")
                    val response = LlamaBridge.generate(contextHandle, prompt, 4096)
                    if (response.isNotBlank() && !response.startsWith("Error:")) {
                        AppLogger.i(TAG, "On-device CI/CD generation completed successfully")
                        return@withContext cleanYamlOutput(response)
                    } else {
                        AppLogger.w(TAG, "Native LLM returned error output: $response")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Native CI/CD generation failed", e)
                }
            }
        }

        throw IllegalStateException("No active GGUF model or responsive Local Server available to generate CI/CD workflow.")
    }

    suspend fun chat(
        userMessage: String,
        systemPrompt: String = "",
        isThinkMode: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        if (!isLoaded()) {
            autoStartService()
        }
        val isThinking = isReasoningModel(preferencesManager.getModelPath() ?: "") && isThinkMode
        val fullPrompt = PromptBuilder.buildChatPrompt(userMessage, systemPrompt, isThinking)
        val localServerUrl = preferencesManager.getLocalServerUrl().trim()

        // 1. Try Local Server if configured
        if (localServerUrl.isNotBlank()) {
            AppLogger.i(TAG, "Sending chat to local server: $localServerUrl")
            val serverResp = queryLocalServer(fullPrompt, 4096, localServerUrl)
            if (!serverResp.isNullOrBlank()) {
                return@withContext cleanChatOutput(serverResp)
            }
        }

        // 2. Try On-Device Native LLM
        if (contextHandle != 0L) {
            inferenceMutex.withLock {
                try {
                    AppLogger.i(TAG, "Running chat inference via on-device native LLM...")
                    val response = LlamaBridge.generate(contextHandle, fullPrompt, 4096)
                    if (response.isNotBlank() && !response.startsWith("Error:")) {
                        return@withContext cleanChatOutput(response)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Native chat inference failed", e)
                }
            }
        }

        throw IllegalStateException("No active GGUF model or responsive Local Server available for chat.")
    }

    fun isReasoningModel(modelPathOrName: String): Boolean {
        val lower = modelPathOrName.lowercase()
        return lower.contains("deepseek-r1") ||
               lower.contains("r1-distill") ||
               lower.contains("qwq") ||
               lower.contains("reasoning") ||
               lower.contains("-r1-") ||
               lower.endsWith("-r1")
    }

    suspend fun isCurrentModelThinking(): Boolean = withContext(Dispatchers.IO) {
        val path = preferencesManager.getModelPath() ?: ""
        isReasoningModel(path)
    }

    fun chatStream(
        userMessage: String,
        systemPrompt: String = "",
        isThinkMode: Boolean = true
    ): Flow<String> = channelFlow {
        if (!isLoaded()) {
            autoStartService()
        }
        val isThinking = isReasoningModel(preferencesManager.getModelPath() ?: "") && isThinkMode
        val fullPrompt = PromptBuilder.buildChatPrompt(userMessage, systemPrompt, isThinking)
        val localServerUrl = preferencesManager.getLocalServerUrl().trim()
        val stopWords = listOf("<|im_end|>", "<|im_start|>", "<|endoftext|>", "<|eot_id|>", "</s>", "<end_of_turn>")

        // 1. If Local Server URL is configured, try it first
        if (localServerUrl.isNotBlank()) {
            AppLogger.i(TAG, "Initiating stream from local server: $localServerUrl")
            var receivedAnyToken = false
            var stopped = false
            try {
                val streamSuccess = streamFromLocalServer(fullPrompt, 4096, localServerUrl) { piece ->
                    if (stopped) return@streamFromLocalServer
                    if (stopWords.any { piece.contains(it) }) {
                        stopped = true
                        return@streamFromLocalServer
                    }
                    receivedAnyToken = true
                    trySend(piece)
                }
                if (streamSuccess && receivedAnyToken) {
                    AppLogger.i(TAG, "Local server streaming completed successfully")
                    return@channelFlow
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Local server streaming failed, falling back to on-device LLM", e)
            }
        }

        // 2. On-Device Native LLM
        if (contextHandle != 0L) {
            inferenceMutex.withLock {
                try {
                    AppLogger.i(TAG, "Streaming chat tokens from on-device native LLM (prompt: ${fullPrompt.length} chars)...")
                    var tokenCount = 0
                    var stopped = false
                    val result = LlamaBridge.generateStream(contextHandle, fullPrompt, 4096) { piece ->
                        if (stopped) return@generateStream
                        if (stopWords.any { piece.contains(it) }) {
                            stopped = true
                            return@generateStream
                        }
                        tokenCount++
                        trySend(piece)
                    }

                    if (result.startsWith("Error:")) {
                        AppLogger.e(TAG, "Native generation error: $result")
                        trySend("\n\n⚠️ **Inference Notice:** $result")
                    } else {
                        AppLogger.i(TAG, "Native streaming finished ($tokenCount tokens emitted)")
                    }
                    return@channelFlow
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Native streaming generation failed with exception", e)
                    throw e
                }
            }
        }

        // 3. If neither worked:
        val errorMsg = if (localServerUrl.isNotBlank()) {
            "Could not connect to Local Server at $localServerUrl and no active on-device GGUF model is loaded."
        } else {
            "No active GGUF model loaded in memory for on-device inference. Please download a model or configure a Local Server URL in Settings."
        }
        AppLogger.e(TAG, errorMsg)
        throw IllegalStateException(errorMsg)
    }.flowOn(Dispatchers.IO)

    private fun normalizeServerUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }

    private fun queryLocalServer(prompt: String, maxTokens: Int, serverUrl: String): String? {
        val base = normalizeServerUrl(serverUrl)
        val endpointsToTry = listOf(
            if (base.endsWith("/v1/chat/completions") || base.endsWith("/api/chat")) base else "$base/v1/chat/completions",
            "$base/api/generate",
            "$base/completion"
        )

        for (endpoint in endpointsToTry) {
            try {
                AppLogger.d(TAG, "Trying local server POST $endpoint")
                val jsonBody = JSONObject().apply {
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("prompt", prompt)
                    put("max_tokens", maxTokens)
                    put("temperature", 0.3)
                    put("stream", false)
                }

                val request = Request.Builder()
                    .url(endpoint)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    AppLogger.w(TAG, "Local server $endpoint returned HTTP ${response.code}")
                    continue
                }

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                if (json.has("choices")) {
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val first = choices.getJSONObject(0)
                        if (first.has("message")) {
                            val content = first.getJSONObject("message").optString("content", "")
                            if (content.isNotBlank()) return content
                        } else if (first.has("text")) {
                            val text = first.optString("text", "")
                            if (text.isNotBlank()) return text
                        }
                    }
                } else if (json.has("response")) {
                    // Ollama /api/generate format
                    val resp = json.optString("response", "")
                    if (resp.isNotBlank()) return resp
                } else if (json.has("content")) {
                    val content = json.optString("content", "")
                    if (content.isNotBlank()) return content
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Local server endpoint $endpoint failed: ${e.localizedMessage ?: e.message}")
            }
        }
        return null
    }

    private fun streamFromLocalServer(
        prompt: String,
        maxTokens: Int,
        serverUrl: String,
        onToken: (String) -> Unit
    ): Boolean {
        val base = normalizeServerUrl(serverUrl)
        val endpoint = if (base.endsWith("/v1/chat/completions") || base.endsWith("/api/chat")) base else "$base/v1/chat/completions"

        return try {
            AppLogger.i(TAG, "Opening SSE stream to $endpoint")
            val jsonBody = JSONObject().apply {
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("prompt", prompt)
                put("max_tokens", maxTokens)
                put("temperature", 0.3)
                put("stream", true)
            }

            val request = Request.Builder()
                .url(endpoint)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "Local server stream failed with HTTP ${response.code}")
                return false
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return false))
            var line: String?
            var receivedAny = false

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("data:") && !l.contains("[DONE]")) {
                    val data = l.removePrefix("data:").trim()
                    try {
                        val obj = JSONObject(data)
                        val choices = obj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "") ?: ""
                            if (content.isNotEmpty()) {
                                receivedAny = true
                                onToken(content)
                            }
                        }
                    } catch (ignored: Exception) {}
                } else if (l.startsWith("{") && l.endsWith("}")) {
                    // Ollama JSON per-line stream
                    try {
                        val obj = JSONObject(l)
                        val messageObj = obj.optJSONObject("message")
                        val content = messageObj?.optString("content", "") ?: obj.optString("response", "")
                        if (content.isNotEmpty()) {
                            receivedAny = true
                            onToken(content)
                        }
                    } catch (ignored: Exception) {}
                }
            }
            reader.close()
            receivedAny
        } catch (e: Exception) {
            AppLogger.e(TAG, "Local server streaming exception", e)
            false
        }
    }

    private fun cleanYamlOutput(raw: String): String {
        return raw.replace(Regex("^```ya?ml\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*$"), "")
            .trim()
    }

    private fun cleanChatOutput(raw: String): String {
        var clean = raw
        val stopWords = listOf(
            "<|im_end|>",
            "<|im_start|>",
            "<|endoftext|>",
            "<|eot_id|>",
            "</s>",
            "<end_of_turn>",
            "\nUser:",
            "\nAssistant:"
        )
        for (stop in stopWords) {
            if (clean.contains(stop)) {
                clean = clean.substringBefore(stop)
            }
        }
        return clean.trimEnd()
    }

    fun isLoaded(): Boolean {
        return modelHandle != 0L && contextHandle != 0L
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        inferenceMutex.withLock {
            loadMutex.withLock {
                unloadInternal()
                _modelState.value = ModelState.NotLoaded
            }
        }
    }

    fun unloadSync() {
        unloadInternal()
        _modelState.value = ModelState.NotLoaded
    }

    private fun unloadInternal() {
        if (contextHandle != 0L) {
            try { LlamaBridge.freeContext(contextHandle) } catch (ignored: Throwable) {}
            contextHandle = 0L
        }
        if (modelHandle != 0L) {
            try { LlamaBridge.freeModel(modelHandle) } catch (ignored: Throwable) {}
            modelHandle = 0L
        }
    }

    private fun parseReviewResult(response: String, metrics: InferenceMetrics? = null): ReviewResult {
        return try {
            val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(response)
            val json = jsonMatch?.value ?: response
            val adapter = moshi.adapter(ReviewResult::class.java)
            val parsed = adapter.fromJson(json) ?: ReviewResult(summary = response)
            parsed.copy(metrics = metrics)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse structured JSON from AI review output, returning raw response", e)
            ReviewResult(
                hasIssue = true,
                summary = response.take(150),
                issues = listOf(
                    CodeIssue(
                        file = "diff",
                        line = 1,
                        severity = "info",
                        description = response,
                        fix = "Apply suggested review changes"
                    )
                ),
                metrics = metrics
            )
        }
    }
}
