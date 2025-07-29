package com.example.service

import com.example.entity.Product
import com.example.repository.GoodProductRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: GoodProductRepository
) {
    
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    // GOOD: Using repository method with specific columns
    fun getProductSummariesByCategory(categoryId: Long) = 
        productRepository.findProductSummaryByCategoryId(categoryId)

    // GOOD: Proper pagination
    fun getProductsInPriceRange(minPrice: BigDecimal, maxPrice: BigDecimal, page: Int, size: Int) =
        productRepository.findProductsInPriceRange(minPrice, maxPrice, 
            org.springframework.data.domain.PageRequest.of(page, size))

    // BAD: Native query with potential SQL injection
    fun searchProductsByName(name: String): List<Product> {
        val sql = "SELECT * FROM products WHERE name LIKE '%$name%'"
        return entityManager.createNativeQuery(sql, Product::class.java).resultList as List<Product>
    }

    // BAD: N+1 query problem
    fun getProductsWithReviews(): List<ProductWithReviewCount> {
        val products = productRepository.findAll()
        return products.map { product ->
            val reviewCount = entityManager.createQuery(
                "SELECT COUNT(r) FROM ProductReview r WHERE r.product.id = :productId", 
                Long::class.java
            )
            .setParameter("productId", product.id)
            .singleResult
            
            ProductWithReviewCount(product, reviewCount)
        }
    }

    // GOOD: Batch query to avoid N+1
    fun getProductsWithReviewCountOptimized(): List<ProductWithReviewCount> {
        val results = entityManager.createQuery("""
            SELECT p, COUNT(r) 
            FROM Product p 
            LEFT JOIN p.reviews r 
            GROUP BY p
        """).resultList
        
        return results.map { result ->
            val array = result as Array<*>
            val product = array[0] as Product
            val count = array[1] as Long
            ProductWithReviewCount(product, count)
        }
    }

    // BAD: Missing transaction annotation for write operation
    fun updateProductPrice(productId: Long, newPrice: BigDecimal) {
        val sql = "UPDATE products SET price = ? WHERE id = ?"
        entityManager.createNativeQuery(sql)
            .setParameter(1, newPrice)
            .setParameter(2, productId)
            .executeUpdate()
    }

    // GOOD: Proper transaction handling
    @Transactional
    fun updateProductPriceCorrectly(productId: Long, newPrice: BigDecimal) {
        val product = productRepository.findById(productId).orElseThrow()
        // Assuming Product has a method to update price
        productRepository.save(product.copy(price = newPrice))
    }
}

data class ProductWithReviewCount(
    val product: Product,
    val reviewCount: Long
)