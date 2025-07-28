package com.queryanalyzer

import com.queryanalyzer.staticanalysis.SqlParser
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull

class SimpleSmokeTest {

    @Test
    fun `test SQL parser works independently`() {
        val parser = SqlParser()
        
        val testQueries = listOf(
            "SELECT * FROM users WHERE active = true",
            "UPDATE orders SET status = 'completed'",
            "DELETE FROM logs",
            "CREATE TABLE test (id INT, name VARCHAR(50))"
        )

        testQueries.forEach { sql ->
            val parsed = parser.parseQuery(sql)
            assertNotNull(parsed, "Should parse SQL: $sql")
            assertTrue(parsed!!.originalSql.isNotEmpty(), "Parsed SQL should not be empty")
        }
        
        println("✅ SQL parser working for ${testQueries.size} test queries")
    }
}