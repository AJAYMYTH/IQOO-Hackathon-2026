package com.apexos.repoguardian.data.llm

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineeringTask(val displayName: String, val idealTier: String) {
    QUICK_CHAT("Quick Chat / Q&A", "0.5B - 1.5B"),
    DELTA_DIGEST("Daily Delta Digest", "0.5B - 1.5B"),
    CODE_REVIEW("Commit Diff Code Review", "1.5B - 3B Coder"),
    FIX_ISSUE("Remediation Patch Generation", "1.5B - 3B Coder"),
    VERIFY_TEST("Automated Unit Test Verification", "1.5B - 3B Coder"),
    DEEP_REASONING("Deep Architecture & Logic Audit", "Deep Reasoning (R1 / 3B+)"),
    CICD_GENERATION("CI/CD Workflow Synthesis", "1.5B - 3B Coder")
}

data class RouteDecision(
    val task: EngineeringTask,
    val selectedModelPath: String?,
    val modelName: String,
    val executionTier: String,
    val hardwareBackend: String,
    val isLocalServer: Boolean = false,
    val rationale: String
)

@Singleton
class ModelRouter @Inject constructor() {

    fun routeTask(
        task: EngineeringTask,
        availableModelFiles: List<File>,
        configuredModelPath: String?,
        localServerUrl: String?,
        backendPreference: String = "auto"
    ): RouteDecision {
        val hasLocalServer = !localServerUrl.isNullOrBlank()

        // 1. If Local Server is configured and active, route heavy tasks or respect server preference
        if (hasLocalServer && (task == EngineeringTask.DEEP_REASONING || task == EngineeringTask.CODE_REVIEW || availableModelFiles.isEmpty())) {
            return RouteDecision(
                task = task,
                selectedModelPath = null,
                modelName = "Local Server ($localServerUrl)",
                executionTier = "LAN Workstation / High-Compute Node",
                hardwareBackend = "Local Server RPC",
                isLocalServer = true,
                rationale = "Routed to local server for maximum context window and compute throughput on ${task.displayName}."
            )
        }

        // Determine optimal on-device hardware backend
        val hardware = when (backendPreference.lowercase()) {
            "npu" -> "Snapdragon Hexagon NPU"
            "gpu" -> "Adreno GPU (Vulkan Compute)"
            "cpu" -> "ARM NEON CPU (Multi-core)"
            else -> "Snapdragon NPU / ARM NEON"
        }

        // 2. If user explicitly configured a valid model, check if it fits the task
        if (!configuredModelPath.isNullOrBlank()) {
            val file = File(configuredModelPath)
            if (file.exists() && file.length() > 0) {
                val name = file.name
                val tier = classifyModelTier(name)
                return RouteDecision(
                    task = task,
                    selectedModelPath = configuredModelPath,
                    modelName = name,
                    executionTier = tier,
                    hardwareBackend = hardware,
                    isLocalServer = false,
                    rationale = "Using active model ($tier) on $hardware."
                )
            }
        }

        // 3. Auto-select from available on-device model files based on task requirements
        if (availableModelFiles.isNotEmpty()) {
            val chosen = pickBestModelForTask(task, availableModelFiles)
            val tier = classifyModelTier(chosen.name)
            return RouteDecision(
                task = task,
                selectedModelPath = chosen.absolutePath,
                modelName = chosen.name,
                executionTier = tier,
                hardwareBackend = hardware,
                isLocalServer = false,
                rationale = "Intelligently selected ${chosen.name} ($tier) optimized for ${task.displayName}."
            )
        }

        // 4. Fallback when no model is found
        return RouteDecision(
            task = task,
            selectedModelPath = null,
            modelName = "No Model Loaded",
            executionTier = "Awaiting Model Download",
            hardwareBackend = hardware,
            isLocalServer = false,
            rationale = "Download a recommended model from the AI Models screen to enable on-device inference."
        )
    }

    private fun pickBestModelForTask(task: EngineeringTask, files: List<File>): File {
        return when (task) {
            EngineeringTask.DEEP_REASONING -> {
                files.find { isReasoningModel(it.name) }
                    ?: files.find { it.name.contains("3b", ignoreCase = true) }
                    ?: files.maxByOrNull { it.length() }
                    ?: files.first()
            }
            EngineeringTask.CODE_REVIEW, EngineeringTask.FIX_ISSUE, EngineeringTask.VERIFY_TEST, EngineeringTask.CICD_GENERATION -> {
                files.find { it.name.contains("coder", ignoreCase = true) && !it.name.contains("0.5b", ignoreCase = true) }
                    ?: files.find { it.name.contains("1.5b", ignoreCase = true) || it.name.contains("3b", ignoreCase = true) }
                    ?: files.first()
            }
            EngineeringTask.QUICK_CHAT, EngineeringTask.DELTA_DIGEST -> {
                files.find { it.name.contains("0.5b", ignoreCase = true) }
                    ?: files.find { it.name.contains("1.5b", ignoreCase = true) }
                    ?: files.minByOrNull { it.length() }
                    ?: files.first()
            }
        }
    }

    fun classifyModelTier(filename: String): String {
        val lower = filename.lowercase()
        return when {
            lower.contains("0.5b") -> "0.5B (Ultra-Fast Mobile)"
            lower.contains("1.5b") && isReasoningModel(lower) -> "1.5B (Deep Reasoning R1)"
            lower.contains("1.5b") -> "1.5B (Code Specialist)"
            lower.contains("3b") -> "3B (Pro Code & Audit)"
            lower.contains("7b") || lower.contains("8b") -> "7B+ (Full Workstation)"
            isReasoningModel(lower) -> "Deep Reasoning Engine"
            else -> "On-Device Neural Model"
        }
    }

    fun isReasoningModel(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("deepseek-r1") ||
               lower.contains("r1-distill") ||
               lower.contains("qwq") ||
               lower.contains("reasoning")
    }
}
