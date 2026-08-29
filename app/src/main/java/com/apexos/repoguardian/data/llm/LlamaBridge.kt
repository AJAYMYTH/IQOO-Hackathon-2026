package com.apexos.repoguardian.data.llm

object LlamaBridge {
    val isAvailable: Boolean = try {
        System.loadLibrary("llama_bridge")
        true
    } catch (e: Throwable) {
        false
    }

    // Load model from GGUF file path, returns model handle (pointer)
    // nGpuLayers: 0 = CPU only, >0 = offload layers to GPU/NPU
    external fun loadModel(modelPath: String, nGpuLayers: Int = 0): Long

    // Create inference context with given model handle
    // contextSize: token context window (4096 for deep code review and repository context)
    external fun createContext(modelHandle: Long, contextSize: Int = 4096): Long

    // Generate completion from prompt, returns generated text
    // maxTokens: max tokens to generate (up to context window)
    external fun generate(contextHandle: Long, prompt: String, maxTokens: Int = 4096): String

    // Generate completion with real-time streaming callback per token piece
    external fun generateStream(
        contextHandle: Long,
        prompt: String,
        maxTokens: Int = 4096,
        onToken: (String) -> Unit
    ): String

    // Free resources
    external fun freeContext(contextHandle: Long)
    external fun freeModel(modelHandle: Long)

    // Get model info string (for display)
    external fun getModelInfo(modelHandle: Long): String
}
