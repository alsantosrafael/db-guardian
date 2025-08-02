package com.dbguardian.core

import com.dbguardian.core.domain.AnalysisConfig
import com.dbguardian.core.domain.AnalysisMode
import com.dbguardian.core.domain.AnalysisRun
import com.dbguardian.core.domain.AnalysisSummary
import com.dbguardian.reporting.AnalysisReport
import com.dbguardian.reporting.ReportLocation
import com.dbguardian.reporting.S3ReportStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.springframework.beans.factory.annotation.Qualifier

/**
 * Application Service - practical DDD approach
 * Coordinates operations and delegates business logic to domain entities
 */
@Service
@Transactional
class AnalysisService(
    private val analysisRunRepository: AnalysisRunRepository,
    private val reportStorage: S3ReportStorage,
    @Qualifier("ioExecutor") private val ioExecutor: Executor
) {
    
    fun execute(config: AnalysisConfig): UUID {
        val mode = when (config.source.lowercase()) {
            "repo", "file" -> AnalysisMode.STATIC
            "text" -> AnalysisMode.DYNAMIC
            else -> AnalysisMode.STATIC
        }
        
        val analysisRun = AnalysisRun(
            mode = mode,
            config = config
        )
        
        val savedRun = analysisRunRepository.save(analysisRun)
        return savedRun.id
    }

    fun getReportUrl(runId: UUID): String? {
        val analysisRun = analysisRunRepository.findById(runId).orElse(null) ?: return null
        
        return analysisRun.getReportLocation()?.let { (bucket, key) ->
            val location = ReportLocation(
                type = "s3",
                bucket = bucket,
                key = key,
                filePath = null
            )
            reportStorage.generateAccessUrl(location)
        }
    }
    
    fun failAnalysis(runId: UUID) {
        val analysisRun = analysisRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Analysis run not found: $runId") }
        
        analysisRun.fail()
        analysisRunRepository.save(analysisRun)
    }
    
    fun getAnalysisRun(runId: UUID): AnalysisRun? {
        return analysisRunRepository.findById(runId).orElse(null)
    }

    fun completeAnalysisWithReportConcurrent(runId: UUID, report: AnalysisReport) {
        val analysisRun = analysisRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Analysis run not found: $runId") }
        
        val overallStart = System.currentTimeMillis()
        println("🔍 Starting concurrent storage operations...")
        
        // JSON report storage (I/O operation - use ioExecutor with virtual threads)
        val jsonReportFuture = CompletableFuture.supplyAsync({
            val operationStart = System.currentTimeMillis()
            println("🟡 JSON report storage started at: ${operationStart - overallStart}ms (ioExecutor)")
            val result = reportStorage.storeReport(report)
            val operationEnd = System.currentTimeMillis()
            println("🟢 JSON report storage completed in: ${operationEnd - operationStart}ms")
            result
        }, ioExecutor)

        // Markdown report storage (I/O operation - use ioExecutor with virtual threads)
        val markdownReportFuture = CompletableFuture.runAsync({
            val operationStart = System.currentTimeMillis()
            println("🟡 Markdown report storage started at: ${operationStart - overallStart}ms (ioExecutor)")
            reportStorage.storeMarkdownReport(report)
            val operationEnd = System.currentTimeMillis()
            println("🟢 Markdown report storage completed in: ${operationEnd - operationStart}ms")
        }, ioExecutor)

        val summary = AnalysisSummary(
            totalIssues = report.summary.totalIssues,
            criticalIssues = report.summary.criticalIssues,
            warningIssues = report.summary.warningIssues,
            infoIssues = report.summary.infoIssues,
            filesAnalyzed = report.summary.filesAnalyzed,
            queriesAnalyzed = report.summary.queriesAnalyzed
        )

        // Database update (depends on JSON completion) - I/O operation, use ioExecutor
        val databaseUpdateFuture = jsonReportFuture.thenCompose { reportLocation ->
            CompletableFuture.runAsync({
                val operationStart = System.currentTimeMillis()
                println("🟡 Database update started at: ${operationStart - overallStart}ms (ioExecutor)")
                analysisRun.complete(summary)
                analysisRun.attachReport(reportLocation.bucket ?: "", reportLocation.key ?: "")
                analysisRunRepository.save(analysisRun)
                val operationEnd = System.currentTimeMillis()
                println("🟢 Database update completed in: ${operationEnd - operationStart}ms")
            }, ioExecutor)
        }

        CompletableFuture.allOf(markdownReportFuture, databaseUpdateFuture).join()
        
        val totalTime = System.currentTimeMillis() - overallStart
        println("⏱️ Total concurrent operations time: ${totalTime}ms")
    }
}