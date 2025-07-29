package com.example.entity

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "products", indexes = [
    Index(name = "idx_product_category", columnList = "category_id"),
    Index(name = "idx_product_name", columnList = "name"),
    Index(name = "idx_product_sku", columnList = "sku", unique = true)
])
data class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name", nullable = false, length = 255)
    val name: String,

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    val sku: String,

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    val price: BigDecimal,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    val category: Category,

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    val reviews: List<ProductReview> = emptyList(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Version
    val version: Long = 0
)