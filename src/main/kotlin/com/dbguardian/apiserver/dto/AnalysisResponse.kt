package com.dbguardian.apiserver.dto

import java.util.UUID


data class AnalysisResponse(
    val runId: UUID,
    val status: String
)

data class ReportResponse(
    val runId: UUID,
    val status: String,
    val summary: String,
    val critical: Int,
    val warnings: Int,
    val reportUrl: String? = null,
    val report: String? = null // Inline report content
)

