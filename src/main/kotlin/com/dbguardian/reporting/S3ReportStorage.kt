package com.dbguardian.reporting

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
    private val reportFormatter: ReportFormatter,
    @Value("\${storage.s3.bucket}") private val bucketName: String
) {
    
    private val keyPrefix = "reports/"
    
    fun storeReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val key = "${keyPrefix}${timestamp}/${report.runId}.json"
        
        val reportContent = reportFormatter.formatAsJson(report)
        
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
            type = "s3",
            bucket = bucketName,
            key = key,
            filePath = null
        )
    }
    
    
    fun storeMarkdownReport(report: AnalysisReport): ReportLocation {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val key = "${keyPrefix}${timestamp}/${report.runId}.md"
        
        val markdownContent = reportFormatter.formatAsMarkdown(report)
        
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
            type = "s3",
            bucket = bucketName,
            key = key,
            filePath = null
        )
    }
    
    fun generateAccessUrl(location: ReportLocation, expirationMinutes: Long = 15): String {
        return when (location.type) {
            "s3" -> generatePresignedUrl(location.bucket!!, location.key!!, expirationMinutes)
            else -> throw IllegalArgumentException("Unsupported location type: ${location.type}")
        }
    }
    
    private fun generatePresignedUrl(bucket: String, key: String, expirationMinutes: Long = 15): String {
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

