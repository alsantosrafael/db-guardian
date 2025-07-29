package com.dbguardian.apiserver

import com.dbguardian.apiserver.dto.*
import com.dbguardian.coreanalysis.AnalysisService
import com.dbguardian.coreanalysis.domain.*
import com.dbguardian.staticanalysis.SqlStaticAnalyzer
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping("/api")
class AnalysisController(
    private val analysisService: AnalysisService,
    private val sqlStaticAnalyzer: SqlStaticAnalyzer
) {
    
    @PostMapping("/analyze")
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
            
            // Run analysis asynchronously
            CompletableFuture.runAsync {
                try {
                    when (request.mode.lowercase()) {
                        "static" -> sqlStaticAnalyzer.analyzeCode(runId, config)
                        "dynamic" -> {
                            // Dynamic analysis not implemented in Phase 1
                            throw UnsupportedOperationException("Dynamic analysis not yet implemented")
                        }
                        else -> throw IllegalArgumentException("Invalid mode: ${request.mode}")
                    }
                } catch (e: Exception) {
                    analysisService.failAnalysis(runId)
                }
            }
            
            return ResponseEntity.ok(AnalysisResponse(runId = runId, status = "started"))
            
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AnalysisResponse(runId = UUID.randomUUID(), status = "error: ${e.message}"))
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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to generate report: ${e.message}"))
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