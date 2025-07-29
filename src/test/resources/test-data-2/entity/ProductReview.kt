package com.example.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "product_reviews", indexes = [
    Index(name = "idx_review_product", columnList = "product_id"),
    Index(name = "idx_review_rating", columnList = "rating"),
    Index(name = "idx_review_created", columnList = "created_at")
])
data class ProductReview(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: Product,

    @Column(name = "rating", nullable = false)
    val rating: Int,

    @Column(name = "comment", columnDefinition = "TEXT")
    val comment: String?,

    @Column(name = "reviewer_name", nullable = false, length = 100)
    val reviewerName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)