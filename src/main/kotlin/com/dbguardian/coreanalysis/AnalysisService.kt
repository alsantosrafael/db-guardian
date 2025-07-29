package com.dbguardian.coreanalysis

import com.dbguardian.coreanalysis.domain.AnalysisConfig
import com.dbguardian.coreanalysis.domain.AnalysisMode
import com.dbguardian.coreanalysis.domain.AnalysisRun
import com.dbguardian.coreanalysis.domain.AnalysisSummary
import com.dbguardian.reporting.AnalysisReport
import com.dbguardian.reporting.ReportStorage
import com.dbguardian.reporting.ReportLocation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Application Service - practical DDD approach
 * Coordinates operations and delegates business logic to domain entities
 */
@Service
@Transactional
class AnalysisService(
    private val analysisRunRepository: AnalysisRunRepository,
    private val reportStorage: ReportStorage
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
        when (reportLocation.type) {
            "s3" -> analysisRun.attachReport(reportLocation.bucket ?: "", reportLocation.key ?: "")
            "local" -> analysisRun.attachReport("local", reportLocation.filePath ?: "")
        }
        
        analysisRunRepository.save(analysisRun)
    }
    
    fun getReportUrl(runId: UUID): String? {
        val analysisRun = analysisRunRepository.findById(runId).orElse(null) ?: return null
        
        return analysisRun.getReportLocation()?.let { (bucket, key) ->
            val location = ReportLocation(
                type = if (bucket == "local") "local" else "s3",
                bucket = if (bucket == "local") null else bucket,
                key = if (bucket == "local") null else key,
                filePath = if (bucket == "local") key else null
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
}