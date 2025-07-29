package com.example.repository

import com.example.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface GoodProductRepository : JpaRepository<Product, Long> {

    // GOOD: Specific columns, proper indexing expected
    @Query("SELECT p.id, p.name, p.price, p.sku FROM Product p WHERE p.category.id = :categoryId")
    fun findProductSummaryByCategoryId(@Param("categoryId") categoryId: Long): List<ProductSummary>

    // GOOD: Using method query with proper naming
    fun findBySkuIgnoreCase(sku: String): Product?

    // GOOD: Paginated query with specific columns
    @Query("""
        SELECT p.id, p.name, p.price, c.name as categoryName 
        FROM Product p 
        JOIN p.category c 
        WHERE p.price BETWEEN :minPrice AND :maxPrice
        ORDER BY p.price ASC
    """)
    fun findProductsInPriceRange(
        @Param("minPrice") minPrice: BigDecimal,
        @Param("maxPrice") maxPrice: BigDecimal,
        pageable: Pageable
    ): Page<ProductPriceView>

    // GOOD: Fetch join to avoid N+1
    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.name LIKE :name%")
    fun findByNameStartingWithAndFetchCategory(@Param("name") name: String): List<Product>

    // GOOD: Count query for pagination
    @Query("SELECT COUNT(p) FROM Product p WHERE p.category.name = :categoryName")
    fun countByCategoryName(@Param("categoryName") categoryName: String): Long

    // GOOD: Bulk update with proper WHERE clause
    @Query("UPDATE Product p SET p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    fun updateTimestamp(@Param("id") id: Long): Int
}

data class ProductSummary(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val sku: String
)

data class ProductPriceView(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val categoryName: String
)