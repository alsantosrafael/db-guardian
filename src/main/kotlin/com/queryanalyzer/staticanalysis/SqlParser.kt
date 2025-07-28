package com.queryanalyzer.staticanalysis

import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.select.Select
import org.springframework.stereotype.Component

@Component
class SqlParser {
    
    fun parseQuery(sql: String): ParsedQuery? {
        return try {
            val statement = CCJSqlParserUtil.parse(sql)
            ParsedQuery(
                originalSql = sql,
                statement = statement,
                isSelect = statement is Select
            )
        } catch (e: JSQLParserException) {
            null // Return null for unparseable queries
        }
    }
    
    fun parseMultipleQueries(sqlContent: String): List<ParsedQuery> {
        val queries = mutableListOf<ParsedQuery>()
        
        // Split by semicolon and try to parse each statement
        val statements = sqlContent.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        statements.forEach { sql ->
            parseQuery(sql)?.let { queries.add(it) }
        }
        
        return queries
    }
}

data class ParsedQuery(
    val originalSql: String,
    val statement: Statement,
    val isSelect: Boolean
)