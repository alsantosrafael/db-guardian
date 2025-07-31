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
        
        // Advanced SQL pattern analysis
        queries.forEach { (parsedQuery, filePath) ->
            issues.addAll(detectAdvancedPatterns(parsedQuery, filePath))
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
            
            // Check for JPA/Hibernate patterns first (these create "virtual" queries)
            if (file.extension.lowercase() in setOf("kt", "java", "scala", "groovy")) {
                queries.addAll(extractJpaPatterns(content, file.absolutePath))
            }
            
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
    
    private fun extractJpaPatterns(content: String, filePath: String): List<ParsedQuery> {
        val virtualQueries = mutableListOf<ParsedQuery>()
        
        try {
            // N+1 Query Detection - @OneToMany without fetch strategy
            val oneToManyPattern = Regex("@OneToMany(?!.*fetch\\s*=\\s*FetchType\\.EAGER)(?!.*@BatchSize)", 
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            
            oneToManyPattern.findAll(content).forEach { match ->
                val contextCode = getContextAroundMatch(content, match.range)
                // Create a virtual SELECT statement that can be analyzed
                val virtualSql = "JPA_N_PLUS_ONE_RISK: $contextCode"
                val parsedStatement = sqlParser.parseQuery("SELECT 1 as virtual_query")
                if (parsedStatement != null) {
                    virtualQueries.add(ParsedQuery(
                        originalSql = virtualSql,
                        statement = parsedStatement.statement,
                        isSelect = true
                    ))
                }
            }
            
            // Lazy loading issues in repositories
            val findAllPattern = Regex("fun\\s+findAll\\s*\\(\\s*\\)\\s*:", RegexOption.IGNORE_CASE)
            if (findAllPattern.containsMatchIn(content) && content.contains("@OneToMany")) {
                findAllPattern.findAll(content).forEach { match ->
                    val contextCode = getContextAroundMatch(content, match.range)
                    val virtualSql = "JPA_FIND_ALL_WITH_COLLECTIONS: $contextCode"
                    val parsedStatement = sqlParser.parseQuery("SELECT 1 as virtual_query")
                    if (parsedStatement != null) {
                        virtualQueries.add(ParsedQuery(
                            originalSql = virtualSql,
                            statement = parsedStatement.statement,
                            isSelect = true
                        ))
                    }
                }
            }
            
            // @Query without JOIN FETCH
            val queryPattern = Regex("@Query\\s*\\(\\s*[\"']([^\"']*SELECT[^\"']*)[\"']", RegexOption.IGNORE_CASE)
            queryPattern.findAll(content).forEach { match ->
                val query = match.groupValues[1].uppercase()
                if (query.contains("JOIN") && !query.contains("JOIN FETCH") && !query.contains("FETCH")) {
                    val virtualSql = "JPA_MISSING_JOIN_FETCH: ${match.groupValues[1]}"
                    val parsedStatement = sqlParser.parseQuery("SELECT 1 as virtual_query")
                    if (parsedStatement != null) {
                        virtualQueries.add(ParsedQuery(
                            originalSql = virtualSql,
                            statement = parsedStatement.statement,
                            isSelect = true
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            println("⚠️ Error extracting JPA patterns from ${filePath}: ${e.message}")
        }
        
        return virtualQueries
    }
    
    private fun getContextAroundMatch(content: String, range: IntRange, contextLines: Int = 2): String {
        val lines = content.lines()
        val startLine = getLineNumber(content, range.first) - 1
        val start = maxOf(0, startLine - contextLines)
        val end = minOf(lines.size, startLine + contextLines + 1)
        
        return lines.subList(start, end).joinToString("\n")
    }
    
    private fun getLineNumber(content: String, position: Int): Int {
        return content.substring(0, position).count { it == '\n' } + 1
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
    
    private fun detectAdvancedPatterns(parsedQuery: ParsedQuery, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        val queryText = parsedQuery.originalSql.uppercase()
        val originalQuery = parsedQuery.originalSql
        
        // JPA/Hibernate specific patterns
        if (originalQuery.startsWith("JPA_")) {
            issues.addAll(detectJpaPatterns(originalQuery, filePath))
        } else {
            // Regular SQL patterns
            issues.addAll(detectBasicSqlPatterns(queryText, originalQuery, filePath))
            issues.addAll(detectPerformanceIssues(queryText, originalQuery, filePath))
            issues.addAll(detectJoinIssues(queryText, originalQuery, filePath))
            issues.addAll(detectIndexIssues(queryText, originalQuery, filePath))
        }
        
        return issues
    }
    
    private fun detectJpaPatterns(originalQuery: String, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        
        when {
            originalQuery.startsWith("JPA_N_PLUS_ONE_RISK:") -> {
                issues.add(createIssue(
                    severity = "CRITICAL",
                    technique = "N_PLUS_ONE_QUERY_RISK",
                    description = "@OneToMany relationship without explicit fetch strategy may cause N+1 queries",
                    suggestion = "Add fetch = FetchType.EAGER, @BatchSize annotation, or use @Query with JOIN FETCH",
                    queryText = originalQuery.substringAfter(":").trim(),
                    filePath = filePath,
                    confidence = 0.8
                ))
            }
            originalQuery.startsWith("JPA_FIND_ALL_WITH_COLLECTIONS:") -> {
                issues.add(createIssue(
                    severity = "WARNING", 
                    technique = "FIND_ALL_WITH_COLLECTIONS",
                    description = "findAll() on entities with @OneToMany collections may cause performance issues",
                    suggestion = "Use custom @Query with JOIN FETCH or implement pagination",
                    queryText = originalQuery.substringAfter(":").trim(),
                    filePath = filePath,
                    confidence = 0.7
                ))
            }
            originalQuery.startsWith("JPA_MISSING_JOIN_FETCH:") -> {
                issues.add(createIssue(
                    severity = "INFO",
                    technique = "MISSING_JOIN_FETCH",
                    description = "@Query with JOIN but no FETCH may not initialize relationships properly",  
                    suggestion = "Consider using JOIN FETCH to eagerly load related entities",
                    queryText = originalQuery.substringAfter(":").trim(),
                    filePath = filePath,
                    confidence = 0.6
                ))
            }
        }
        
        return issues
    }
    
    private fun detectBasicSqlPatterns(queryText: String, originalQuery: String, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        
        // SELECT * detection
        if (queryText.contains("SELECT *")) {
            issues.add(createIssue(
                severity = "WARNING",
                technique = "AVOID_SELECT_STAR",
                description = "Using SELECT * can lead to performance issues and breaks when schema changes",
                suggestion = "Replace SELECT * with explicit column names",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 0.9
            ))
        }
        
        // Missing WHERE clause in UPDATE/DELETE
        if (queryText.contains("UPDATE") && !queryText.contains("WHERE")) {
            issues.add(createIssue(
                severity = "CRITICAL",
                technique = "REQUIRE_WHERE_CLAUSE",
                description = "UPDATE without WHERE clause can modify all rows",
                suggestion = "Add WHERE clause to UPDATE statement",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 1.0
            ))
        }
        
        if (queryText.contains("DELETE") && !queryText.contains("WHERE")) {
            issues.add(createIssue(
                severity = "CRITICAL",
                technique = "REQUIRE_WHERE_CLAUSE",
                description = "DELETE without WHERE clause can remove all rows",
                suggestion = "Add WHERE clause to DELETE statement",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 1.0
            ))
        }
        
        return issues
    }
    
    private fun detectPerformanceIssues(queryText: String, originalQuery: String, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        
        // COUNT(*) without LIMIT on potentially large tables
        if (queryText.contains("COUNT(*)") && !queryText.contains("LIMIT")) {
            issues.add(createIssue(
                severity = "WARNING",
                technique = "COUNT_WITHOUT_LIMIT",
                description = "COUNT(*) on large tables can be expensive",
                suggestion = "Consider using COUNT(*) with LIMIT or approximate counting for large datasets",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 0.7
            ))
        }
        
        // LIKE patterns starting with %
        val likeWildcardPattern = Regex("LIKE\\s+['\"]%[^'\"]*['\"]", RegexOption.IGNORE_CASE)
        if (likeWildcardPattern.containsMatchIn(queryText)) {
            issues.add(createIssue(
                severity = "WARNING",
                technique = "INEFFICIENT_LIKE_PATTERN",
                description = "LIKE patterns starting with % cannot use indexes efficiently",
                suggestion = "Consider full-text search or restructuring the query to avoid leading wildcards",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 0.9
            ))
        }
        
        // Multiple OR conditions that could be IN
        if (queryText.count { it.toString().uppercase() == "OR" } >= 3) {
            val orPattern = Regex("\\w+\\s*=\\s*[^\\s]+\\s+OR\\s+\\w+\\s*=", RegexOption.IGNORE_CASE)
            if (orPattern.containsMatchIn(queryText)) {
                issues.add(createIssue(
                    severity = "INFO",
                    technique = "MULTIPLE_OR_CONDITIONS",
                    description = "Multiple OR conditions on the same column can often be optimized",
                    suggestion = "Consider using IN clause instead of multiple OR conditions",
                    queryText = originalQuery,
                    filePath = filePath,
                    confidence = 0.6
                ))
            }
        }
        
        // ORDER BY without LIMIT (potential performance issue)
        if (queryText.contains("ORDER BY") && !queryText.contains("LIMIT") && !queryText.contains("WHERE")) {
            issues.add(createIssue(
                severity = "WARNING",
                technique = "ORDER_BY_WITHOUT_LIMIT",
                description = "ORDER BY on full table scan without LIMIT can be expensive",
                suggestion = "Consider adding LIMIT clause or WHERE conditions to reduce dataset size",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 0.6
            ))
        }
        
        return issues
    }
    
    private fun detectJoinIssues(queryText: String, originalQuery: String, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        
        // Potential Cartesian joins (multiple FROM tables without JOIN keywords)
        val fromPattern = Regex("FROM\\s+(\\w+(?:\\s*,\\s*\\w+)+)", RegexOption.IGNORE_CASE)
        val joinPattern = Regex("\\s+JOIN\\s+", RegexOption.IGNORE_CASE)
        
        val fromMatch = fromPattern.find(queryText)
        if (fromMatch != null && !joinPattern.containsMatchIn(queryText)) {
            val tableCount = fromMatch.groupValues[1].split(",").size
            if (tableCount > 1) {
                issues.add(createIssue(
                    severity = "CRITICAL",
                    technique = "POTENTIAL_CARTESIAN_JOIN",
                    description = "Multiple tables in FROM clause without explicit JOIN may create Cartesian product",
                    suggestion = "Use explicit JOIN clauses instead of comma-separated tables in FROM",
                    queryText = originalQuery,
                    filePath = filePath,
                    confidence = 0.8
                ))
            }
        }
        
        // JOINs without proper conditions
        if (queryText.contains("JOIN") && !queryText.contains("ON") && !queryText.contains("USING")) {
            issues.add(createIssue(
                severity = "WARNING",
                technique = "JOIN_WITHOUT_CONDITION",
                description = "JOIN clause found without ON or USING condition",
                suggestion = "Ensure all JOINs have proper join conditions to avoid unexpected results",
                queryText = originalQuery,
                filePath = filePath,
                confidence = 0.7
            ))
        }
        
        return issues
    }
    
    private fun detectIndexIssues(queryText: String, originalQuery: String, filePath: String): List<ReportIssue> {
        val issues = mutableListOf<ReportIssue>()
        
        // WHERE clause on potentially unindexed columns (hint for optimization)
        val wherePattern = Regex("WHERE[^;]*?(\\w+)\\s*[=<>!]", RegexOption.IGNORE_CASE)
        val whereMatches = wherePattern.findAll(queryText)
        
        for (match in whereMatches) {
            val column = match.groupValues[1].lowercase()
            // Skip common indexed columns
            if (!isLikelyIndexedColumn(column)) {
                issues.add(createIssue(
                    severity = "INFO",
                    technique = "POTENTIAL_MISSING_INDEX",
                    description = "WHERE clause on column '$column' may benefit from an index",
                    suggestion = "Consider adding an index on column '$column' if this query runs frequently",
                    queryText = originalQuery,
                    filePath = filePath,
                    confidence = 0.5
                ))
                break // Only report once per query to avoid noise
            }
        }
        
        return issues
    }
    
    private fun isLikelyIndexedColumn(column: String): Boolean {
        val commonIndexedColumns = setOf(
            "id", "uuid", "primary_key", "pk",
            "created_at", "updated_at", "timestamp",
            "status", "type", "category_id", "user_id"
        )
        return commonIndexedColumns.any { column.contains(it) }
    }
    
    private fun createIssue(
        severity: String,
        technique: String,
        description: String,
        suggestion: String,
        queryText: String,
        filePath: String,
        confidence: Double
    ): ReportIssue {
        return ReportIssue(
            id = UUID.randomUUID(),
            severity = severity,
            technique = technique,
            description = description,
            suggestion = suggestion,
            queryText = queryText,
            location = IssueLocation(filePath, 1, 1, null),
            confidence = confidence
        )
    }
}