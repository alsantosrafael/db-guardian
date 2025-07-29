package com.dbguardian.reporting

interface ReportStorage {
    fun storeReport(report: AnalysisReport): ReportLocation
    fun storeMarkdownReport(report: AnalysisReport): ReportLocation
    fun generateAccessUrl(location: ReportLocation, expirationMinutes: Long = 15): String
}

data class ReportLocation(
    val type: String, // "s3" or "local"
    val bucket: String?,
    val key: String?,
    val filePath: String?
)