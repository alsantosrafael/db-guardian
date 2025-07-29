package com.example.service

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ReportingService {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    // BAD: Complex query with multiple subqueries causing performance issues
    fun generateComplexReport(): List<Map<String, Any>> {
        val sql = """
            SELECT *,
                   (SELECT COUNT(*) FROM products p2 WHERE p2.category_id = p.category_id) as category_count,
                   (SELECT AVG(price) FROM products p3 WHERE p3.category_id = p.category_id) as avg_category_price,
                   (SELECT COUNT(*) FROM product_reviews pr WHERE pr.product_id = p.id) as review_count,
                   (SELECT MAX(rating) FROM product_reviews pr2 WHERE pr2.product_id = p.id) as max_rating
            FROM products p
            ORDER BY category_id, name
        """
        
        return entityManager.createNativeQuery(sql).resultList as List<Map<String, Any>>
    }

    // BAD: No LIMIT on large aggregation
    fun getProductStatisticsWithoutLimit(): List<Map<String, Any>> {
        val sql = """
            SELECT 
                c.name as category_name,
                COUNT(*) as product_count,
                AVG(p.price) as avg_price,
                MAX(p.price) as max_price,
                MIN(p.price) as min_price
            FROM products p, categories c
            WHERE p.category_id = c.id
            GROUP BY c.id, c.name
            ORDER BY product_count DESC
        """
        
        return entityManager.createNativeQuery(sql).resultList as List<Map<String, Any>>
    }

    // BAD: Dynamic SQL construction (SQL injection risk)
    fun getDynamicReport(filters: Map<String, String>): List<Map<String, Any>> {
        var sql = "SELECT * FROM products WHERE 1=1"
        
        filters.forEach { (key, value) ->
            sql += " AND $key = '$value'"
        }
        
        return entityManager.createNativeQuery(sql).resultList as List<Map<String, Any>>
    }

    // BAD: Cartesian product in complex join
    fun getProductsWithAllRelatedData(): List<Map<String, Any>> {
        val sql = """
            SELECT *
            FROM products p, 
                 categories c, 
                 product_reviews pr
            WHERE p.price > 50
              AND c.id > 0
              AND pr.rating >= 4
        """
        
        return entityManager.createNativeQuery(sql).resultList as List<Map<String, Any>>
    }

    // GOOD: Proper JOIN with specific columns
    fun getOptimizedProductReport(): List<ProductReportItem> {
        val sql = """
            SELECT 
                p.id, 
                p.name, 
                p.price, 
                c.name as category_name,
                COUNT(pr.id) as review_count,
                COALESCE(AVG(pr.rating), 0) as avg_rating
            FROM products p
            INNER JOIN categories c ON p.category_id = c.id
            LEFT JOIN product_reviews pr ON p.id = pr.product_id
            GROUP BY p.id, p.name, p.price, c.name
            ORDER BY p.name
            LIMIT 1000
        """
        
        val results = entityManager.createNativeQuery(sql).resultList
        return results.map { result ->
            val array = result as Array<*>
            ProductReportItem(
                id = array[0] as Long,
                name = array[1] as String,
                price = array[2] as BigDecimal,
                categoryName = array[3] as String,
                reviewCount = array[4] as Long,
                avgRating = array[5] as Double
            )
        }
    }
}

data class ProductReportItem(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val categoryName: String,
    val reviewCount: Long,
    val avgRating: Double
)