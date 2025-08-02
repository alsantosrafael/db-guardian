package com.dbguardian.coreanalysis

import com.dbguardian.coreanalysis.domain.AnalysisConfig
import com.dbguardian.coreanalysis.domain.AnalysisStatus
import com.dbguardian.staticanalysis.SqlStaticAnalyzer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AsyncAnalysisService(
    private val analysisService: AnalysisService,
    private val sqlStaticAnalyzer: SqlStaticAnalyzer
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    @Async("ioExecutor")
    fun executeAnalysisAsync(runId: UUID, config: AnalysisConfig, mode: String) {
        try {
            when (mode.lowercase()) {
                "static" -> sqlStaticAnalyzer.analyzeCode(runId, config)
                "dynamic" -> {
                    throw UnsupportedOperationException("Dynamic analysis not yet implemented")
                }
                else -> throw IllegalArgumentException("Invalid mode: $mode")
            }
        } catch (e: Exception) {
            logger.error("Analysis failed for runId $runId: ${e.message}", e)
            // Only fail analysis if it hasn't already completed successfully
            val analysisRun = analysisService.getAnalysisRun(runId)
            if (analysisRun?.status != AnalysisStatus.COMPLETED) {
                analysisService.failAnalysis(runId)
            }
        }
    }
}