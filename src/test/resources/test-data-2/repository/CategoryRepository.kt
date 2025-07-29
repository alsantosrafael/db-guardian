package com.example.repository

import com.example.entity.Category
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CategoryRepository : JpaRepository<Category, Long> {

    // GOOD: Specific columns with proper join
    @Query("SELECT c.id, c.name FROM Category c WHERE c.parent IS NULL")
    fun findRootCategoryIds(): List<CategoryIdName>

    // BAD: SELECT * with unnecessary data
    @Query("SELECT * FROM categories WHERE parent_id IS NULL")
    fun findAllRootCategories(): List<Category>

    // GOOD: Recursive query with CTE (if supported)
    @Query(value = """
        WITH RECURSIVE category_tree AS (
            SELECT id, name, parent_id, 0 as level
            FROM categories 
            WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, c.name, c.parent_id, ct.level + 1
            FROM categories c
            INNER JOIN category_tree ct ON c.parent_id = ct.id
        )
        SELECT id, name, level FROM category_tree ORDER BY level, name
    """, nativeQuery = true)
    fun findCategoryHierarchy(): List<CategoryHierarchy>

    // BAD: Leading wildcard search
    @Query("SELECT * FROM categories WHERE name LIKE '%:search%'")
    fun searchCategoriesWithWildcard(@Param("search") search: String): List<Category>
}

data class CategoryIdName(val id: Long, val name: String)
data class CategoryHierarchy(val id: Long, val name: String, val level: Int)