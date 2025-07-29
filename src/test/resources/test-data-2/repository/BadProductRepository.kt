package com.example.repository

import com.example.entity.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.repository.query.Param

interface BadProductRepository : JpaRepository<Product, Long> {

    // BAD: SELECT *
    @Query("SELECT * FROM products")
    fun findAllProductsUsingSelectStar(): List<Product>

    // BAD: Leading wildcard LIKE
    @Query("SELECT * FROM products WHERE name LIKE '%:search%'")
    fun searchProductsWithLeadingWildcard(@Param("search") search: String): List<Product>

    // BAD: Cartesian product (missing JOIN condition)
    @Query("SELECT * FROM products p, categories c WHERE p.price > 100")
    fun findExpensiveProductsWithCartesianProduct(): List<Product>

    // BAD: Non-SARGable WHERE clause
    @Query("SELECT * FROM products WHERE UPPER(name) = UPPER(:name)")
    fun findByNameIgnoreCaseNonSargable(@Param("name") name: String): List<Product>

    // BAD: Missing WHERE in UPDATE
    @Modifying
    @Query("UPDATE products SET updated_at = NOW()")
    fun updateAllProductTimestamps(): Int

    // BAD: Missing WHERE in DELETE
    @Modifying
    @Query("DELETE FROM products")
    fun deleteAllProducts(): Int

    // BAD: SQL injection risk with native query
    @Query(value = "SELECT * FROM products WHERE name = ?1", nativeQuery = true)
    fun findByNameUnsafe(name: String): List<Product>

    // BAD: Complex subquery in SELECT causing performance issues
    @Query("""
        SELECT *, 
               (SELECT COUNT(*) FROM product_reviews r WHERE r.product_id = p.id) as review_count,
               (SELECT AVG(r.rating) FROM product_reviews r WHERE r.product_id = p.id) as avg_rating
        FROM products p
    """)
    fun findAllWithSubqueryInSelect(): List<Product>

    // BAD: No LIMIT on potentially large result set
    @Query("SELECT * FROM products ORDER BY created_at DESC")
    fun findAllOrderedByDateWithoutLimit(): List<Product>

    // BAD: Function on indexed column prevents index usage
    @Query("SELECT * FROM products WHERE DATE(created_at) = CURRENT_DATE")
    fun findCreatedTodayNonSargable(): List<Product>
}