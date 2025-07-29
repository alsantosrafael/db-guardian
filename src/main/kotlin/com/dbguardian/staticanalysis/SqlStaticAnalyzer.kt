package com.dbguardian.staticanalysis

import com.dbguardian.coreanalysis.AnalysisService
import com.dbguardian.coreanalysis.domain.*
import com.dbguardian.reporting.*
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/**
 * Static analyzer focused on JVM environments
 * Analyzes Spring Data JPA, Hibernate, JDBC, and SQL files
 * Uses basic pattern matching for SQL issue detection
 */
@Service
class SqlStaticAnalyzer(
    private val sqlParser: SqlParser,
    private val analysisService: AnalysisService
) {
    
    fun analyzeCode(runId: UUID, config: AnalysisConfig) {
        try {
            val sqlFiles = findJvmSqlFiles(config)
            println("🔍 Found ${sqlFiles.size} JVM/SQL files to analyze: ${sqlFiles.map { it.absolutePath }}")
            
            val allQueries = mutableListOf<Pair<ParsedQuery, String>>()
            
            sqlFiles.forEach { file ->
                println("📄 Processing: ${file.absolutePath}")
                val queries = extractAndParseQueries(file)
                queries.forEach { query ->
                    allQueries.add(query to file.absolutePath)
                }
            }
            
            println("🔍 Found ${allQueries.size} SQL queries to analyze")
            
            // Simple analysis without RAG - just basic SQL parsing results
            val report = generateBasicReport(runId, allQueries)
            
            // Complete analysis with S3 report storage
            println("💾 Storing analysis report to S3 and updating database")
            analysisService.completeAnalysisWithReport(runId, report)
            println("✅ Analysis completed successfully")
            
        } catch (e: Exception) {
            analysisService.failAnalysis(runId)
            throw e
        }
    }
    
    private fun generateBasicReport(runId: UUID, queries: List<Pair<ParsedQuery, String>>): AnalysisReport {
        val issues = mutableListOf<ReportIssue>()
        
        // Basic SQL analysis without RAG - just count and categorize queries
        queries.forEach { (parsedQuery, filePath) ->
            // Simple heuristics for demo purposes
            val queryText = parsedQuery.originalSql.uppercase()
            
            when {
                queryText.contains("SELECT *") -> {
                    issues.add(ReportIssue(
                        id = UUID.randomUUID(),
                        severity = "WARNING",
                        technique = "AVOID_SELECT_STAR",
                        description = "Using SELECT * can lead to performance issues",
                        suggestion = "Replace SELECT * with explicit column names",
                        queryText = parsedQuery.originalSql,
                        location = IssueLocation(filePath, 1, 1, null),
                        confidence = 0.9
                    ))
                }
                queryText.contains("UPDATE") && !queryText.contains("WHERE") -> {
                    issues.add(ReportIssue(
                        id = UUID.randomUUID(),
                        severity = "CRITICAL",
                        technique = "REQUIRE_WHERE_CLAUSE",
                        description = "UPDATE without WHERE clause can modify all rows",
                        suggestion = "Add WHERE clause to UPDATE statement",
                        queryText = parsedQuery.originalSql,
                        location = IssueLocation(filePath, 1, 1, null),
                        confidence = 1.0
                    ))
                }
                queryText.contains("DELETE") && !queryText.contains("WHERE") -> {
                    issues.add(ReportIssue(
                        id = UUID.randomUUID(),
                        severity = "CRITICAL",
                        technique = "REQUIRE_WHERE_CLAUSE",
                        description = "DELETE without WHERE clause can remove all rows",
                        suggestion = "Add WHERE clause to DELETE statement",
                        queryText = parsedQuery.originalSql,
                        location = IssueLocation(filePath, 1, 1, null),
                        confidence = 1.0
                    ))
                }
            }
        }
        
        val summary = ReportSummary(
            totalIssues = issues.size,
            criticalIssues = issues.count { it.severity == "CRITICAL" },
            warningIssues = issues.count { it.severity == "WARNING" },
            infoIssues = issues.count { it.severity == "INFO" },
            filesAnalyzed = queries.map { it.second }.distinct().size,
            queriesAnalyzed = queries.size
        )
        
        return AnalysisReport(
            runId = runId,
            mode = "static",
            status = "COMPLETED",
            startedAt = LocalDateTime.now(),
            completedAt = LocalDateTime.now(),
            summary = summary,
            issues = issues
        )
    }
    
    private fun findJvmSqlFiles(config: AnalysisConfig): List<File> {
        val files = mutableListOf<File>()
        
        // First, check the main source path
        val sourceFile = File(config.source)
        if (sourceFile.exists()) {
            if (sourceFile.isFile) {
                files.add(sourceFile)
            } else if (sourceFile.isDirectory) {
                files.addAll(sourceFile.walkTopDown()
                    .filter { it.isFile && (isSqlFile(it) || isJvmCodeFile(it) || isJvmConfigFile(it)) }
                    .toList())
            }
        }
        
        // Check migration paths for SQL files (additional paths)
        config.getMigrationPathsList().forEach { path ->
            val dir = File(path.trim())
            if (dir.exists() && dir.isDirectory) {
                files.addAll(dir.walkTopDown()
                    .filter { it.isFile && (isSqlFile(it) || isJvmCodeFile(it)) }
                    .toList())
            }
        }
        
        // Check schema path (additional paths)
        config.schemaPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                files.add(file)
            }
        }
        
        // Check code path for JVM files (additional paths)
        config.codePath?.let { path ->
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                files.addAll(dir.walkTopDown()
                    .filter { it.isFile && (isSqlFile(it) || isJvmCodeFile(it) || isJvmConfigFile(it)) }
                    .toList())
            }
        }
        
        return files.distinct()
    }
    
    private fun isSqlFile(file: File): Boolean {
        val sqlExtensions = setOf("sql", "ddl", "dml", "pgsql", "mysql", "psql")
        return file.extension.lowercase() in sqlExtensions
    }
    
    private fun isJvmCodeFile(file: File): Boolean {
        val jvmExtensions = setOf("kt", "java", "scala", "groovy")
        return file.extension.lowercase() in jvmExtensions
    }
    
    private fun isJvmConfigFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        val name = file.nameWithoutExtension.lowercase()
        
        // JVM configuration files
        if (extension in setOf("yml", "yaml", "xml", "properties")) {
            return true
        }
        
        // JVM database-related files
        if (name.contains("migration") || name.contains("schema") || name.contains("database") || 
            name.contains("entity") || name.contains("repository") || name.contains("dao") ||
            name.contains("hibernate") || name.contains("jpa")) {
            return true
        }
        
        return false
    }
    
    private fun extractAndParseQueries(file: File): List<ParsedQuery> {
        val queries = mutableListOf<ParsedQuery>()
        
        try {
            val content = file.readText()
            val sqlStrings = when (file.extension.lowercase()) {
                in setOf("sql", "ddl", "dml", "pgsql", "mysql", "psql") -> {
                    extractFromSqlFile(content)
                }
                in setOf("kt", "java", "scala", "groovy") -> {
                    extractFromJvmCodeFile(content)
                }
                in setOf("yml", "yaml", "xml", "properties") -> {
                    extractFromJvmConfigFile(content)
                }
                else -> emptyList()
            }
            
            sqlStrings.forEach { (query, _) ->
                val parsedQuery = sqlParser.parseQuery(query)
                if (parsedQuery != null) {
                    queries.add(parsedQuery)
                }
            }
            
        } catch (e: Exception) {
            println("⚠️ Error processing file ${file.absolutePath}: ${e.message}")
        }
        
        return queries
    }
    
    private fun extractFromSqlFile(content: String): List<Pair<String, Int>> {
        val queries = mutableListOf<Pair<String, Int>>()
        val lines = content.lines()
        
        var currentQuery = StringBuilder()
        var queryStartLine = 1
        
        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            
            // Skip comments and empty lines
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("--") || trimmedLine.startsWith("/*")) {
                return@forEachIndexed
            }
            
            currentQuery.append(line).append("\n")
            
            // Check if query ends with semicolon
            if (trimmedLine.endsWith(";")) {
                val query = currentQuery.toString().trim()
                if (query.isNotEmpty() && containsSqlKeywords(query)) {
                    queries.add(query to queryStartLine)
                }
                currentQuery = StringBuilder()
                queryStartLine = index + 2
            }
        }
        
        // Handle query without ending semicolon
        if (currentQuery.isNotEmpty()) {
            val query = currentQuery.toString().trim()
            if (query.isNotEmpty() && containsSqlKeywords(query)) {
                queries.add(query to queryStartLine)
            }
        }
        
        return queries
    }
    
    private fun extractFromJvmCodeFile(content: String): List<Pair<String, Int>> {
        val queries = mutableListOf<Pair<String, Int>>()
        val lines = content.lines()
        
        lines.forEachIndexed { index, line ->
            // Look for JVM SQL patterns
            val jvmSqlQueries = extractJvmSqlPatterns(line)
            jvmSqlQueries.forEach { query ->
                if (query.isNotEmpty() && containsSqlKeywords(query)) {
                    queries.add(query to index + 1)
                }
            }
        }
        
        return queries
    }
    
    private fun extractJvmSqlPatterns(line: String): List<String> {
        val results = mutableListOf<String>()
        
        // JVM string patterns - more sophisticated than naive cross-language approach
        val patterns = listOf(
            // Multi-line strings in Kotlin
            "\"\"\"([^\"]*(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)[^\"]*)\"\"\"".toRegex(RegexOption.IGNORE_CASE),
            // Regular strings
            "\"([^\"]*(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE),
            // Spring Data JPA @Query annotations
            "@Query\\s*\\(\\s*\"([^\"]*(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE),
            // Hibernate @NamedQuery
            "@NamedQuery\\s*\\(.*query\\s*=\\s*\"([^\"]*(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE),
            // JDBC PreparedStatement patterns
            "prepareStatement\\s*\\(\\s*\"([^\"]*(?:SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)[^\"]*)\"".toRegex(RegexOption.IGNORE_CASE)
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(line).forEach { match ->
                val sql = match.groupValues[1].trim()
                if (sql.isNotEmpty()) {
                    results.add(sql)
                }
            }
        }
        
        return results
    }
    
    private fun extractFromJvmConfigFile(content: String): List<Pair<String, Int>> {
        val queries = mutableListOf<Pair<String, Int>>()
        val lines = content.lines()
        
        lines.forEachIndexed { index, line ->
            // Look for SQL in YAML/XML configuration
            if (line.contains("SELECT", ignoreCase = true) || 
                line.contains("INSERT", ignoreCase = true) ||
                line.contains("UPDATE", ignoreCase = true) ||
                line.contains("DELETE", ignoreCase = true)) {
                
                val cleanedLine = line.trim().removePrefix("-").trim()
                if (containsSqlKeywords(cleanedLine)) {
                    queries.add(cleanedLine to index + 1)
                }
            }
        }
        
        return queries
    }
    
    private fun containsSqlKeywords(text: String): Boolean {
        val sqlKeywords = setOf("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP", "TRUNCATE")
        val upperText = text.uppercase()
        return sqlKeywords.any { keyword -> upperText.contains(keyword) }
    }
}