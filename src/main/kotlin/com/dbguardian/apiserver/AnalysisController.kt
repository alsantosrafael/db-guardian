package com.dbguardian.apiserver

import com.dbguardian.apiserver.dto.AnalysisRequest
import com.dbguardian.apiserver.dto.AnalysisResponse
import com.dbguardian.apiserver.dto.ReportResponse
import com.dbguardian.core.AnalysisService
import com.dbguardian.core.AsyncAnalysisService
import com.dbguardian.core.domain.AnalysisConfig
import com.dbguardian.core.domain.AnalysisStatus
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import java.util.*

@RestController
@RequestMapping("/api/v1")
class AnalysisController(
    private val analysisService: AnalysisService,
    private val asyncAnalysisService: AsyncAnalysisService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/analysis")
    fun startAnalysis(@Valid @RequestBody request: AnalysisRequest): ResponseEntity<AnalysisResponse> {
        try {
            val config = AnalysisConfig(
                dialect = request.config.dialect,
                source = request.source,
                migrationPaths = request.config.migrationPaths?.let {
                    "[${it.joinToString(",") { "\"$it\"" }}]"
                },
                schemaPath = request.config.schemaPath,
                codePath = request.config.codePath
            )
            
            val runId = analysisService.execute(config)
            
            // Run analysis asynchronously - AsyncAnalysisService already handles @Async execution
            when (request.mode.lowercase()) {
                "static" -> asyncAnalysisService.executeAnalysisAsync(runId, config, "static")
                "dynamic" -> {
                    // Dynamic analysis not implemented in Phase 1
                    throw UnsupportedOperationException("Dynamic analysis not yet implemented")
                }
                else -> throw IllegalArgumentException("Invalid mode: ${request.mode}")
            }
            
            return ResponseEntity.ok(AnalysisResponse(runId = runId, status = AnalysisStatus.STARTED))
            
        } catch (e: Exception) {
            logger.error(e.message)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AnalysisResponse(runId = null, status = AnalysisStatus.FAILED))
        }
    }
    
    @GetMapping("/report/{runId}")
    fun getReport(@PathVariable runId: UUID, @RequestParam(defaultValue = "json") format: String): ResponseEntity<Any> {
        try {
            val analysisRun = analysisService.getAnalysisRun(runId)
                ?: return ResponseEntity.notFound().build()
            
            return when (format.lowercase()) {
                "json" -> {
                    val response = ReportResponse(
                        runId = analysisRun.id,
                        status = analysisRun.status.name.lowercase(),
                        summary = "${analysisRun.totalIssues} issues found",
                        critical = analysisRun.criticalIssues,
                        warnings = analysisRun.warningIssues,
                        reportUrl = analysisService.getReportUrl(analysisRun.id)
                    )
                    ResponseEntity.ok(response)
                }
                
                "url" -> {
                    val reportUrl = analysisService.getReportUrl(analysisRun.id)
                        ?: return ResponseEntity.notFound().build()
                    ResponseEntity.ok(mapOf("reportUrl" to reportUrl))
                }
                
                else -> ResponseEntity.badRequest()
                    .body(mapOf("error" to "Unsupported format: $format. Use 'json' or 'url'"))
            }
            
        } catch (e: Exception) {
            logger.error(e.message)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to generate report: ${e.message}"))
        }
    }

    @GetMapping("/analysis/{runId}/status")
    fun checkStatus(@PathVariable("runId") runId: UUID): ResponseEntity<AnalysisResponse> {
        try {
            val analysisRun = analysisService.getAnalysisRun(runId)
                ?: return ResponseEntity.notFound().build()
            return ResponseEntity.ok().body(AnalysisResponse(analysisRun.id, analysisRun.status))
        } catch (e: Exception) {
            logger.error(e.message)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AnalysisResponse(runId = null, status = AnalysisStatus.FAILED))
        }
    }
    
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf(
            "status" to "UP",
            "service" to "db-guardian",
            "version" to "1.0.0"
        ))
    }
}