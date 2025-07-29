package com.dbguardian.apiserver.dto

import java.time.LocalDateTime

data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int,
    val timestamp: LocalDateTime
) {}
