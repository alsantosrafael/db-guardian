package com.dbguardian.coreanalysis

import com.dbguardian.coreanalysis.domain.AnalysisConfig
import com.dbguardian.coreanalysis.domain.AnalysisMode
import com.dbguardian.coreanalysis.domain.AnalysisRun
import com.dbguardian.coreanalysis.domain.AnalysisSummary
import com.dbguardian.reporting.AnalysisReport
import com.dbguardian.reporting.ReportLocation
import com.dbguardian.reporting.S3ReportStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Application Service - practical DDD approach
 * Coordinates operations and delegates business logic to domain entities
 */
@Service
@Transactional
class AnalysisService(
    private val analysisRunRepository: AnalysisRunRepository,
    private val reportStorage: S3ReportStorage
) {
    
    fun execute(config: AnalysisConfig): UUID {
        // Simple mode determination - business logic
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
    
    fun completeAnalysisWithReport(runId: UUID, report: AnalysisReport) {
        val analysisRun = analysisRunRepository.findById(runId)
            .orElseThrow { IllegalArgumentException("Analysis run not found: $runId") }
        
        // Store report using configured storage
        val reportLocation = reportStorage.storeReport(report)
        reportStorage.storeMarkdownReport(report)
        
        // Use domain logic to complete analysis
        val summary = AnalysisSummary(
            totalIssues = report.summary.totalIssues,
            criticalIssues = report.summary.criticalIssues,
            warningIssues = report.summary.warningIssues,
            infoIssues = report.summary.infoIssues,
            filesAnalyzed = report.summary.filesAnalyzed,
            queriesAnalyzed = report.summary.queriesAnalyzed
        )
        
        analysisRun.complete(summary)
        analysisRun.attachReport(reportLocation.bucket ?: "", reportLocation.key ?: "")
        
        analysisRunRepository.save(analysisRun)
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
        
        // JSON report storage
        val jsonStart = System.currentTimeMillis()
        val jsonReportFuture = CompletableFuture.supplyAsync {
            val operationStart = System.currentTimeMillis()
            println("🟡 JSON report storage started at: ${operationStart - overallStart}ms")
            val result = reportStorage.storeReport(report)
            val operationEnd = System.currentTimeMillis()
            println("🟢 JSON report storage completed in: ${operationEnd - operationStart}ms")
            result
        }

        // Markdown report storage
        val markdownStart = System.currentTimeMillis()
        val markdownReportFuture = CompletableFuture.runAsync {
            val operationStart = System.currentTimeMillis()
            println("🟡 Markdown report storage started at: ${operationStart - overallStart}ms")
            reportStorage.storeMarkdownReport(report)
            val operationEnd = System.currentTimeMillis()
            println("🟢 Markdown report storage completed in: ${operationEnd - operationStart}ms")
        }

        val summary = AnalysisSummary(
            totalIssues = report.summary.totalIssues,
            criticalIssues = report.summary.criticalIssues,
            warningIssues = report.summary.warningIssues,
            infoIssues = report.summary.infoIssues,
            filesAnalyzed = report.summary.filesAnalyzed,
            queriesAnalyzed = report.summary.queriesAnalyzed
        )

        // Database update (depends on JSON completion)
        val databaseUpdateFuture = jsonReportFuture.thenCompose { reportLocation ->
            CompletableFuture.runAsync {
                val operationStart = System.currentTimeMillis()
                println("🟡 Database update started at: ${operationStart - overallStart}ms")
                analysisRun.complete(summary)
                analysisRun.attachReport(reportLocation.bucket ?: "", reportLocation.key ?: "")
                analysisRunRepository.save(analysisRun)
                val operationEnd = System.currentTimeMillis()
                println("🟢 Database update completed in: ${operationEnd - operationStart}ms")
            }
        }

        CompletableFuture.allOf(markdownReportFuture, databaseUpdateFuture).join()
        
        val totalTime = System.currentTimeMillis() - overallStart
        println("⏱️ Total concurrent operations time: ${totalTime}ms")
    }
}