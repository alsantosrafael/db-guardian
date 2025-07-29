package com.dbguardian.reporting

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class LocalReportStorage(
    @Value("\${app.reports.local.directory:./reports}") private val reportsDirectory: String,
    @Value("\${app.reports.local.base-url:file://}") private val baseUrl: String
) : ReportStorage {
    
    init {
        // Ensure reports directory exists
        File(reportsDirectory).mkdirs()
    }
    
    override fun storeReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val relativePath = "$timestamp/${report.runId}.json"
        val filePath = "$reportsDirectory/$relativePath"
        
        val reportContent = formatReportAsJson(report)
        
        // Ensure directory exists
        File(filePath).parentFile.mkdirs()
        
        // Write file
        File(filePath).writeText(reportContent)
        
        return ReportLocation(
            type = "local",
            bucket = null,
            key = null,
            filePath = filePath
        )
    }
    
    override fun storeMarkdownReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val relativePath = "$timestamp/${report.runId}.md" 
        val filePath = "$reportsDirectory/$relativePath"
        
        val markdownContent = formatReportAsMarkdown(report)
        
        // Ensure directory exists
        File(filePath).parentFile.mkdirs()
        
        // Write file
        File(filePath).writeText(markdownContent)
        
        return ReportLocation(
            type = "local",
            bucket = null,
            key = null,
            filePath = filePath
        )
    }
    
    override fun generateAccessUrl(location: ReportLocation, expirationMinutes: Long): String {
        return when (location.type) {
            "local" -> "$baseUrl${location.filePath}"
            else -> throw IllegalArgumentException("Unsupported location type: ${location.type}")
        }
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
        builder.appendLine("*Report generated by Database Guardian*")
        
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
}