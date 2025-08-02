package com.dbguardian.cli

/**
 * Data classes for CLI operations
 * Centralized models to avoid duplication across CLI classes
 */

data class CliConfig(
    val path: String,
    val verbose: Boolean,
    val productionOnly: Boolean
)

data class Issue(
    val severity: String,
    val description: String,
    val filename: String
)

data class AnalysisResult(
    val fileCount: Int,
    val queryCount: Int,
    val issues: List<Issue>
)