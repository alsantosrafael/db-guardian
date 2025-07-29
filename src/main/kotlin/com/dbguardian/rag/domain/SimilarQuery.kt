package com.dbguardian.rag.domain

import java.time.LocalDateTime

data class SimilarQuery(
    val queryId: String,
    val originalQuery: String,
    val similarQuery: String,
    val similarityScore: Double,
    val analysisType: SimilarityType,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
}

enum class SimilarityType{
    LEXICAL,
    STRUCTURAL,
    SEMANTIC
}