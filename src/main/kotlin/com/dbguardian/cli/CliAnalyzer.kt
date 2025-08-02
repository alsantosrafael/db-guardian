package com.dbguardian.cli

import java.io.File

/**
 * Core analysis logic for CLI - separated from presentation concerns
 * Contains the actual rule implementations extracted from SqlStaticAnalyzer
 */
class CliAnalyzer {
    
    fun analyzeFiles(files: List<File>, config: CliConfig): AnalysisResult {
        val issues = mutableListOf<Issue>()
        var queryCount = 0
        
        files.forEach { file ->
            if (config.verbose) {
                println("📄 Processing: ${file.name}")
            }
            
            try {
                val content = file.readText()
                val fileIssues = analyzeFileContent(content, file.name, config)
                issues.addAll(fileIssues)
                
                queryCount += countQueries(content)
                
                if (config.verbose && fileIssues.isNotEmpty()) {
                    fileIssues.forEach { issue ->
                        val icon = when (issue.severity) {
                            "CRITICAL" -> "🚨"
                            "WARNING" -> "⚠️ "
                            else -> "ℹ️ "
                        }
                        println("  $icon ${issue.description}")
                    }
                }
                
            } catch (e: Exception) {
                if (config.verbose) {
                    println("  ⚠️  Could not analyze ${file.name}: ${e.message}")
                }
            }
        }
        
        return AnalysisResult(
            fileCount = files.size,
            queryCount = queryCount,
            issues = issues
        )
    }
    
    private fun analyzeFileContent(content: String, filename: String, config: CliConfig): List<Issue> {
        val issues = mutableListOf<Issue>()
        
        // Skip test files if production-only mode
        val isTestFile = isTestFile(filename, content)
        if (config.productionOnly && isTestFile) {
            return issues
        }
        
        // Apply analysis rules
        issues.addAll(checkSqlInjectionRisk(content, filename, isTestFile))
        issues.addAll(checkUnsafeUpdateDelete(content, filename))
        issues.addAll(checkSelectStar(content, filename, isTestFile))
        issues.addAll(checkInefficiientLike(content, filename))
        issues.addAll(checkModifyingWithoutTransactional(content, filename))
        issues.addAll(checkNativeQueryWithoutParams(content, filename))
        
        return issues
    }
    
    private fun isTestFile(filename: String, content: String): Boolean {
        return filename.contains("Test") || 
               content.contains("/test/") ||
               filename.endsWith("Test.kt") ||
               filename.endsWith("Test.java") ||
               filename.endsWith("Spec.kt")
    }
    
    private fun checkSqlInjectionRisk(content: String, filename: String, isTestFile: Boolean): List<Issue> {
        if (isTestFile) return emptyList()
        
        val issues = mutableListOf<Issue>()
        if (content.contains(Regex("\\+.*(?:SELECT|INSERT|UPDATE|DELETE)|(?:SELECT|INSERT|UPDATE|DELETE).*\\+"))) {
            issues.add(Issue(
                severity = "CRITICAL",
                description = "SQL injection risk - string concatenation in queries",
                filename = filename
            ))
        }
        return issues
    }
    
    private fun checkUnsafeUpdateDelete(content: String, filename: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        val updateDeletePattern = Regex("(?i)(UPDATE|DELETE)\\s+\\w+(?:\\s+SET\\s+.*)?(?!.*WHERE)", RegexOption.DOT_MATCHES_ALL)
        
        if (updateDeletePattern.containsMatchIn(content)) {
            val lines = content.lines()
            val hasUnsafeUpdateDelete = lines.any { line ->
                val trimmed = line.trim()
                !trimmed.startsWith("//") && !trimmed.startsWith("/*") && 
                updateDeletePattern.containsMatchIn(line) && !line.contains("WHERE", ignoreCase = true)
            }
            
            if (hasUnsafeUpdateDelete) {
                issues.add(Issue(
                    severity = "CRITICAL",
                    description = "UPDATE/DELETE without WHERE clause affects all rows",
                    filename = filename
                ))
            }
        }
        return issues
    }
    
    private fun checkSelectStar(content: String, filename: String, isTestFile: Boolean): List<Issue> {
        if (isTestFile) return emptyList()
        
        val issues = mutableListOf<Issue>()
        if (content.contains(Regex("(?i)SELECT\\s*\\*"))) {
            issues.add(Issue(
                severity = "WARNING",
                description = "SELECT * usage (performance/maintainability risk)",
                filename = filename
            ))
        }
        return issues
    }
    
    private fun checkInefficiientLike(content: String, filename: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (content.contains(Regex("(?i)LIKE\\s*['\"]%"))) {
            issues.add(Issue(
                severity = "WARNING",
                description = "LIKE pattern with leading % cannot use indexes",
                filename = filename
            ))
        }
        return issues
    }
    
    private fun checkModifyingWithoutTransactional(content: String, filename: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (content.contains("@Modifying") && !content.contains("@Transactional")) {
            issues.add(Issue(
                severity = "WARNING",
                description = "@Modifying annotation without @Transactional",
                filename = filename
            ))
        }
        return issues
    }
    
    private fun checkNativeQueryWithoutParams(content: String, filename: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        if (content.contains(Regex("nativeQuery\\s*=\\s*true"))) {
            if (!content.contains(Regex(":\\w+|\\?\\d+"))) {
                issues.add(Issue(
                    severity = "CRITICAL",
                    description = "Native query without parameters (injection risk)",
                    filename = filename
                ))
            }
        }
        return issues
    }
    
    private fun countQueries(content: String): Int {
        val sqlKeywords = listOf("SELECT", "INSERT", "UPDATE", "DELETE", "@Query")
        return sqlKeywords.sumOf { keyword ->
            content.split(keyword, ignoreCase = true).size - 1
        }
    }
}