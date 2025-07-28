package com.queryanalyzer.reporting

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class S3ReportStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @Value("\${app.reports.s3.bucket}") private val bucketName: String,
    @Value("\${app.reports.s3.prefix:reports/}") private val keyPrefix: String
) {
    
    fun storeReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val key = "${keyPrefix}${timestamp}/${report.runId}.json"
        
        val reportContent = formatReportAsJson(report)
        
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("application/json")
            .metadata(mapOf(
                "run-id" to report.runId.toString(),
                "mode" to report.mode,
                "total-issues" to report.summary.totalIssues.toString(),
                "critical-issues" to report.summary.criticalIssues.toString()
            ))
            .build()
        
        s3Client.putObject(putRequest, RequestBody.fromString(reportContent))
        
        return ReportLocation(
            bucket = bucketName,
            key = key
        )
    }
    
    private fun formatReportAsJson(report: AnalysisReport): String {
        return """
        {
            "runId": "${report.runId}",
            "status": "${report.status}",
            "mode": "${report.mode}",
            "startedAt": "${report.startedAt}",
            "completedAt": "${report.completedAt}",
            "summary": {
                "totalIssues": ${report.summary.totalIssues},
                "criticalIssues": ${report.summary.criticalIssues},
                "warningIssues": ${report.summary.warningIssues},
                "infoIssues": ${report.summary.infoIssues},
                "filesAnalyzed": ${report.summary.filesAnalyzed},
                "queriesAnalyzed": ${report.summary.queriesAnalyzed}
            },
            "issues": [
                ${report.issues.joinToString(",\n") { formatIssueAsJson(it) }}
            ]
        }
        """.trimIndent()
    }
    
    private fun formatIssueAsJson(issue: ReportIssue): String {
        val locationJson = issue.location?.let { 
            """"location": {
                "filePath": "${it.filePath}",
                "startLine": ${it.startLine},
                "endLine": ${it.endLine}
            },""" 
        } ?: ""
        
        return """
        {
            "id": "${issue.id}",
            "severity": "${issue.severity}",
            "technique": "${issue.technique}",
            "description": "${issue.description.replace("\"", "\\\"")}",
            "suggestion": "${issue.suggestion.replace("\"", "\\\"")}",
            "queryText": "${issue.queryText.replace("\"", "\\\"").replace("\n", "\\n")}",
            $locationJson
            "confidence": ${issue.confidence}
        }""".trimIndent()
    }
    
    fun storeMarkdownReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val key = "${keyPrefix}${timestamp}/${report.runId}.md"
        
        val markdownContent = formatReportAsMarkdown(report)
        
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType("text/markdown")
            .metadata(mapOf(
                "run-id" to report.runId.toString(),
                "format" to "markdown",
                "total-issues" to report.summary.totalIssues.toString(),
                "critical-issues" to report.summary.criticalIssues.toString()
            ))
            .build()
        
        s3Client.putObject(putRequest, RequestBody.fromString(markdownContent))
        
        return ReportLocation(
            bucket = bucketName,
            key = key
        )
    }
    
    private fun formatReportAsMarkdown(report: AnalysisReport): String {
        val builder = StringBuilder()
        
        // Header
        builder.appendLine("# SQL Analysis Report")
        builder.appendLine()
        builder.appendLine("**Analysis ID:** `${report.runId}`")
        builder.appendLine("**Mode:** ${report.mode}")
        builder.appendLine("**Status:** ${report.status}")
        builder.appendLine("**Started:** ${report.startedAt}")
        report.completedAt?.let {
            builder.appendLine("**Completed:** $it")
        }
        builder.appendLine()
        
        // Summary
        builder.appendLine("## Summary")
        builder.appendLine()
        builder.appendLine("- **Total Issues:** ${report.summary.totalIssues}")
        builder.appendLine("- **Critical:** ${report.summary.criticalIssues}")
        builder.appendLine("- **Warnings:** ${report.summary.warningIssues}")
        builder.appendLine("- **Info:** ${report.summary.infoIssues}")
        builder.appendLine("- **Files Analyzed:** ${report.summary.filesAnalyzed}")
        builder.appendLine("- **Queries Analyzed:** ${report.summary.queriesAnalyzed}")
        builder.appendLine()
        
        if (report.issues.isNotEmpty()) {
            // Issues by severity
            formatIssuesBySeverity(builder, report.issues, "CRITICAL", "🚨 Critical Issues")
            formatIssuesBySeverity(builder, report.issues, "WARNING", "⚠️ Warnings")
            formatIssuesBySeverity(builder, report.issues, "INFO", "ℹ️ Information")
            
            // Detailed issues
            builder.appendLine("## Detailed Issues")
            builder.appendLine()
            
            report.issues.forEachIndexed { index, issue ->
                formatIssueDetail(builder, issue, index + 1)
            }
        } else {
            builder.appendLine("## ✅ No Issues Found")
            builder.appendLine()
            builder.appendLine("Great! No SQL issues were detected in the analyzed code.")
            builder.appendLine()
        }
        
        // Footer
        builder.appendLine("---")
        builder.appendLine("*Report generated by Query Analyzer RAG System*")
        
        return builder.toString()
    }
    
    private fun formatIssuesBySeverity(
        builder: StringBuilder,
        issues: List<ReportIssue>,
        severity: String,
        title: String
    ) {
        val filteredIssues = issues.filter { it.severity == severity }
        if (filteredIssues.isNotEmpty()) {
            builder.appendLine("## $title")
            builder.appendLine()
            
            filteredIssues.groupBy { it.technique }.forEach { (technique, techniqueIssues) ->
                builder.appendLine("### $technique")
                builder.appendLine()
                builder.appendLine("**Count:** ${techniqueIssues.size}")
                builder.appendLine("**Description:** ${techniqueIssues.first().description}")
                builder.appendLine()
                
                techniqueIssues.forEach { issue ->
                    val location = issue.location?.let { " (${it.filePath}:${it.startLine})" } ?: ""
                    val confidence = if (issue.confidence < 1.0) " (${(issue.confidence * 100).toInt()}% confidence)" else ""
                    builder.appendLine("- $location$confidence")
                }
                builder.appendLine()
            }
        }
    }
    
    private fun formatIssueDetail(builder: StringBuilder, issue: ReportIssue, index: Int) {
        builder.appendLine("### Issue #$index")
        builder.appendLine()
        builder.appendLine("**Severity:** ${getSeverityEmoji(issue.severity)} ${issue.severity}")
        builder.appendLine("**Technique:** ${issue.technique}")
        builder.appendLine("**Description:** ${issue.description}")
        builder.appendLine("**Confidence:** ${(issue.confidence * 100).toInt()}%")
        
        issue.location?.let { location ->
            builder.appendLine("**Location:** ${location.filePath}:${location.startLine}")
        }
        
        builder.appendLine()
        builder.appendLine("**Query:**")
        builder.appendLine("```sql")
        builder.appendLine(issue.queryText.trim())
        builder.appendLine("```")
        
        builder.appendLine()
        builder.appendLine("**Suggestion:** ${issue.suggestion}")
        
        builder.appendLine()
        builder.appendLine("---")
        builder.appendLine()
    }
    
    private fun getSeverityEmoji(severity: String): String = when (severity.uppercase()) {
        "CRITICAL" -> "🚨"
        "WARNING" -> "⚠️"
        "INFO" -> "ℹ️"
        else -> "❓"
    }
    
    fun generatePresignedUrl(bucket: String, key: String, expirationMinutes: Long = 15): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()
        
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expirationMinutes))
            .getObjectRequest(getObjectRequest)
            .build()
        
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }
}

data class ReportLocation(
    val bucket: String,
    val key: String
)