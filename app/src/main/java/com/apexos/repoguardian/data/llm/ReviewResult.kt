package com.apexos.repoguardian.data.llm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReviewResult(
    @Json(name = "has_issue") val hasIssue: Boolean = false,
    val summary: String = "",
    val issues: List<CodeIssue> = emptyList(),
    @Json(name = "fixed_code") val fixedCode: String? = null
)

@JsonClass(generateAdapter = true)
data class CodeIssue(
    val file: String? = null,
    val line: Int? = null,
    val severity: String = "info", // critical, warning, info
    val description: String = "",
    val fix: String? = null
)
