package com.dbguardian.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Analysis Run Entity - practical DDD approach
 * Contains business logic while being pragmatic about persistence
 */
@Entity
@Table(name = "analysis_runs")
data class AnalysisRun(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    val mode: AnalysisMode,

    @Enumerated(EnumType.STRING)
    var status: AnalysisStatus = AnalysisStatus.STARTED,

    val startedAt: LocalDateTime = LocalDateTime.now(),
    var completedAt: LocalDateTime? = null,

    @Embedded
    val config: AnalysisConfig,
    
    // Store S3 report reference - URLs are generated on demand
    @Column(name = "report_s3_bucket")
    var reportS3Bucket: String? = null,
    @Column(name = "report_s3_key") 
    var reportS3Key: String? = null,
    
    // Summary metrics
    var totalIssues: Int = 0,
    var criticalIssues: Int = 0,
    var warningIssues: Int = 0,
    var infoIssues: Int = 0,
    var filesAnalyzed: Int = 0,
    var queriesAnalyzed: Int = 0
) {
    
    // Business logic methods - this is the practical DDD part
    fun markInProgress() {
        require(status == AnalysisStatus.STARTED) { "Analysis must be started to mark as in progress" }
        status = AnalysisStatus.IN_PROGRESS
    }
    
    fun complete(summary: AnalysisSummary) {
        require(status == AnalysisStatus.IN_PROGRESS || status == AnalysisStatus.STARTED) { 
            "Analysis must be in progress to complete" 
        }
        
        status = AnalysisStatus.COMPLETED
        completedAt = LocalDateTime.now()
        
        // Update summary
        totalIssues = summary.totalIssues
        criticalIssues = summary.criticalIssues
        warningIssues = summary.warningIssues
        infoIssues = summary.infoIssues
        filesAnalyzed = summary.filesAnalyzed
        queriesAnalyzed = summary.queriesAnalyzed
    }
    
    fun fail() {
        require(status != AnalysisStatus.COMPLETED) { "Cannot fail a completed analysis" }
        status = AnalysisStatus.FAILED
        completedAt = LocalDateTime.now()
    }
    
    fun attachReport(bucket: String, key: String) {
        require(status == AnalysisStatus.COMPLETED) { "Can only attach reports to completed analysis" }
        require(bucket.isNotBlank()) { "S3 bucket cannot be blank" }
        require(key.isNotBlank()) { "S3 key cannot be blank" }
        
        reportS3Bucket = bucket
        reportS3Key = key
    }
    
    // Convenience methods
    fun isCompleted() = status == AnalysisStatus.COMPLETED
    fun isFailed() = status == AnalysisStatus.FAILED  
    fun hasReport() = reportS3Bucket != null && reportS3Key != null
    
    fun getReportLocation(): Pair<String, String>? = 
        if (reportS3Bucket != null && reportS3Key != null) {
            reportS3Bucket!! to reportS3Key!!
        } else null
}

/**
 * Simple value object for analysis configuration
 */
@Embeddable
data class AnalysisConfig(
    val dialect: String,
    val source: String,
    val migrationPaths: String? = null, // JSON array as string
    val schemaPath: String? = null,
    val codePath: String? = null
) {
    init {
        require(dialect.isNotBlank()) { "SQL dialect cannot be blank" }
        require(source.isNotBlank()) { "Analysis source cannot be blank" }
    }
    
    fun getMigrationPathsList(): List<String> {
        return migrationPaths?.let { 
            it.removeSurrounding("[", "]")
              .split(",")
              .map { path -> path.trim().removeSurrounding("\"") }
              .filter { path -> path.isNotBlank() }
        } ?: emptyList()
    }
}

/**
 * Simple value object for analysis summary
 */
data class AnalysisSummary(
    val totalIssues: Int,
    val criticalIssues: Int,
    val warningIssues: Int,
    val infoIssues: Int,
    val filesAnalyzed: Int = 0,
    val queriesAnalyzed: Int = 0
) {
    init {
        require(totalIssues >= 0) { "Total issues cannot be negative" }
        require(criticalIssues >= 0) { "Critical issues cannot be negative" }
        require(warningIssues >= 0) { "Warning issues cannot be negative" }
        require(infoIssues >= 0) { "Info issues cannot be negative" }
    }
}

enum class AnalysisMode {
    STATIC, DYNAMIC
}

enum class AnalysisStatus {
    STARTED, IN_PROGRESS, COMPLETED, FAILED
}