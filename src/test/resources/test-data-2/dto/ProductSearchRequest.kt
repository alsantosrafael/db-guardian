package com.example.dto

import java.math.BigDecimal

data class ProductSearchRequest(
    val name: String?,
    val categoryId: Long?,
    val minPrice: BigDecimal?,
    val maxPrice: BigDecimal?,
    val sortBy: String? = "name",
    val sortDirection: String? = "ASC",
    val page: Int = 0,
    val size: Int = 20
)

// GOOD: DTO for specific data transfer
data class ProductSummaryDto(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val categoryName: String,
    val reviewCount: Long,
    val avgRating: Double?
)

// BAD: Exposing internal entity structure
data class ProductFullDto(
    val id: Long,
    val name: String,
    val sku: String,
    val price: BigDecimal,
    val description: String?,
    val category: Any, // Exposing full category entity
    val reviews: List<Any>, // Exposing full review entities
    val createdAt: String,
    val updatedAt: String?,
    val version: Long
)