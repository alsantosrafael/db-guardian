package com.queryanalyzer.apiserver.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class AnalysisRequest(
    @field:NotBlank(message = "Mode is required")
    val mode: String, // "static" or "dynamic"
    
    @field:NotBlank(message = "Source is required")
    val source: String, // "repo/path" or "runtime"
    
    @field:Valid
    @field:NotNull(message = "Config is required")
    val config: AnalysisConfigDto
)

data class AnalysisConfigDto(
    @field:NotBlank(message = "Dialect is required")
    val dialect: String,
    
    val migrationPaths: List<String>? = null,
    val schemaPath: String? = null,
    val codePath: String? = null
)