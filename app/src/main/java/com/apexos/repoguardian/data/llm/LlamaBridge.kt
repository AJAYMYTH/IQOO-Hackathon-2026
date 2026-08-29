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
    // contextSize: token context window (2048 is good for code review)
    external fun createContext(modelHandle: Long, contextSize: Int = 2048): Long

    // Generate completion from prompt, returns generated text
    // maxTokens: max tokens to generate
    external fun generate(contextHandle: Long, prompt: String, maxTokens: Int = 1024): String

    // Free resources
    external fun freeContext(contextHandle: Long)
    external fun freeModel(modelHandle: Long)

    // Get model info string (for display)
    external fun getModelInfo(modelHandle: Long): String
}
