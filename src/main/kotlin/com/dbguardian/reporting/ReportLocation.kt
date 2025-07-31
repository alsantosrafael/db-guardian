package com.dbguardian.reporting

data class ReportLocation(
    val type: String,
    val bucket: String?,
    val key: String?,
    val filePath: String?
)