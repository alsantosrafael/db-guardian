package com.queryanalyzer.reporting

import java.time.LocalDateTime
import java.util.UUID

data class AnalysisReport(
    val runId: UUID,
    val mode: String,
    val status: String,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val summary: ReportSummary,
    val issues: List<ReportIssue>
)

data class ReportSummary(
    val totalIssues: Int,
    val criticalIssues: Int,
    val warningIssues: Int,
    val infoIssues: Int,
    val filesAnalyzed: Int,
    val queriesAnalyzed: Int
)

data class ReportIssue(
    val id: UUID,
    val severity: String,
    val technique: String,
    val description: String,
    val suggestion: String,
    val queryText: String,
    val location: IssueLocation?,
    val confidence: Double
)

data class IssueLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val columnNumber: Int?
)