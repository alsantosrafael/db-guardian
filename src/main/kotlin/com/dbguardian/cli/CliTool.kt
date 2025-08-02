package com.dbguardian.cli

import java.io.File

/**
 * CLI Tool - Entry point for DB Guardian command line interface
 * Lightweight version that uses basic analysis rules without Spring dependencies
 * For full analysis with all advanced features, use the Spring Boot application
 */
object CliTool {
    
    @JvmStatic
    fun main(args: Array<String>) {
        run(args)
    }
    
    fun run(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        when (args[0]) {
            "scan" -> {
                val scanArgs = parseScanArgs(args.drop(1))
                scan(scanArgs)
            }
            "help", "--help", "-h" -> printUsage()
            else -> {
                println("❌ Unknown command: ${args[0]}")
                printUsage()
            }
        }
    }
    
    private fun scan(scanArgs: ScanArgs) {
        println("🛡️  DB Guardian - Spring Boot SQL Linter")
        println("==========================================")
        println()
        
        val startTime = System.currentTimeMillis()
        
        println("🔍 Scanning: ${scanArgs.path}")
        if (scanArgs.productionOnly) {
            println("📋 Mode: Production only (excluding test files)")
        }
        if (scanArgs.verbose) {
            println("📋 Mode: Verbose output enabled")
        }
        println()
        
        // Find relevant files
        val files = findRelevantFiles(scanArgs.path, scanArgs.productionOnly)
        if (files.isEmpty()) {
            println("❌ No relevant files found in ${scanArgs.path}")
            return
        }
        
        println("📁 Files to analyze: ${files.size}")
        println()
        
        // Analyze files using basic rules (extracted from SqlStaticAnalyzer patterns)
        val issues = mutableListOf<Issue>()
        var queryCount = 0
        
        files.forEach { file ->
            val filename = file.name
            
            if (scanArgs.verbose) {
                println("📄 Processing: $filename")
            }
            
            try {
                val content = file.readText()
                val fileIssues = analyzeFileContent(content, filename, scanArgs)
                issues.addAll(fileIssues)
                
                // Count queries in this file
                val fileQueryCount = countQueries(content)
                queryCount += fileQueryCount
                
                if (scanArgs.verbose && fileIssues.isNotEmpty()) {
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
                if (scanArgs.verbose) {
                    println("  ⚠️  Could not analyze $filename: ${e.message}")
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Print results
        printResults(files.size, queryCount, issues, duration)
    }
    
    private fun analyzeFileContent(content: String, filename: String, scanArgs: ScanArgs): List<Issue> {
        val issues = mutableListOf<Issue>()
        
        // Skip test files if production-only mode
        val isTestFile = filename.contains("Test") || content.contains("/test/")
        if (scanArgs.productionOnly && isTestFile) {
            return issues
        }
        
        // Rule 1: SQL Injection - String concatenation (from SqlStaticAnalyzer)
        if (content.contains(Regex("\\+.*(?:SELECT|INSERT|UPDATE|DELETE)|(?:SELECT|INSERT|UPDATE|DELETE).*\\+"))) {
            if (!isTestFile) {
                issues.add(Issue(
                    severity = "CRITICAL",
                    description = "SQL injection risk - string concatenation in queries",
                    filename = filename
                ))
            }
        }
        
        // Rule 2: UPDATE/DELETE without WHERE (from SqlStaticAnalyzer)
        val updateDeletePattern = Regex("(?i)(UPDATE|DELETE)\\s+\\w+(?:\\s+SET\\s+.*)?(?!.*WHERE)", RegexOption.DOT_MATCHES_ALL)
        if (updateDeletePattern.containsMatchIn(content)) {
            // Additional check to skip comments
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
        
        // Rule 3: SELECT * usage (from SqlStaticAnalyzer)
        if (content.contains(Regex("(?i)SELECT\\s*\\*"))) {
            if (!isTestFile) {
                issues.add(Issue(
                    severity = "WARNING",
                    description = "SELECT * usage (performance/maintainability risk)",
                    filename = filename
                ))
            }
        }
        
        // Rule 4: LIKE with leading % (from SqlStaticAnalyzer)
        if (content.contains(Regex("(?i)LIKE\\s*['\"]%"))) {
            issues.add(Issue(
                severity = "WARNING",
                description = "LIKE pattern with leading % cannot use indexes",
                filename = filename
            ))
        }
        
        // Rule 5: @Modifying without @Transactional (Spring Data JPA)
        if (content.contains("@Modifying") && !content.contains("@Transactional")) {
            issues.add(Issue(
                severity = "WARNING",
                description = "@Modifying annotation without @Transactional",
                filename = filename
            ))
        }
        
        // Rule 6: Native queries without parameters
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
    
    private fun findRelevantFiles(path: String, productionOnly: Boolean): List<File> {
        val rootFile = File(path)
        if (!rootFile.exists()) {
            return emptyList()
        }
        
        val files = if (rootFile.isFile) {
            listOf(rootFile)
        } else {
            rootFile.walkTopDown()
                .filter { it.isFile }
                .filter { file ->
                    val ext = file.extension.lowercase()
                    ext in setOf("kt", "java", "sql") ||
                    file.name.contains("Repository") ||
                    file.name.contains("Entity") ||
                    file.name.contains("migration")
                }
                .filter { file ->
                    if (productionOnly) {
                        !file.path.contains("/test/") && 
                        !file.name.endsWith("Test.kt") && 
                        !file.name.endsWith("Test.java") &&
                        !file.name.endsWith("Spec.kt")
                    } else {
                        true
                    }
                }
                .take(100)
                .toList()
        }
        
        return files
    }
    
    private fun printResults(fileCount: Int, queryCount: Int, issues: List<Issue>, duration: Long) {
        println()
        println("📊 Analysis Results")
        println("===================")
        println("⏱️  Analysis time: ${duration}ms")
        println("📁 Files analyzed: $fileCount")
        println("🔍 Queries found: $queryCount")
        
        val critical = issues.filter { it.severity == "CRITICAL" }
        val warnings = issues.filter { it.severity == "WARNING" }
        val info = issues.filter { it.severity == "INFO" }
        
        println("🚨 Critical issues: ${critical.size}")
        println("⚠️  Warning issues: ${warnings.size}")
        println("ℹ️  Info issues: ${info.size}")
        println("📋 Total issues: ${issues.size}")
        
        if (issues.isEmpty()) {
            println()
            println("✅ No issues found! Your SQL looks clean.")
        } else {
            if (critical.isNotEmpty()) {
                println()
                println("🚨 CRITICAL Issues Found (${critical.size})")
                println("======================================")
                critical.forEach { issue ->
                    println("  • ${issue.filename}: ${issue.description}")
                }
            }
            
            if (warnings.isNotEmpty()) {
                println()
                println("⚠️  WARNING Issues Found (${warnings.size})")
                println("=====================================")
                warnings.forEach { issue ->
                    println("  • ${issue.filename}: ${issue.description}")
                }
            }
            
            if (info.isNotEmpty()) {
                println()
                println("ℹ️  INFO Issues Found (${info.size})")
                println("=============================")
                info.forEach { issue ->
                    println("  • ${issue.filename}: ${issue.description}")
                }
            }
            
            println()
            println("💡 Focus on CRITICAL issues first, then WARNING.")
            println("🔧 For complete analysis with all rules, use: ./gradlew bootRun")
        }
        
        println()
    }
    
    private fun parseScanArgs(args: List<String>): ScanArgs {
        var path = "."
        var verbose = false
        var productionOnly = false
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--verbose", "-v" -> verbose = true
                "--production-only", "-p" -> productionOnly = true
                else -> {
                    if (!args[i].startsWith("-")) {
                        path = args[i]
                    }
                }
            }
            i++
        }
        
        return ScanArgs(path, verbose, productionOnly)
    }
    
    private fun printUsage() {
        println("🛡️  DB Guardian - Spring Boot SQL Linter")
        println("==========================================")
        println()
        println("Usage:")
        println("  CliTool scan [path] [options]")
        println()
        println("Commands:")
        println("  scan [path]        Analyze SQL in the given path (default: current directory)")
        println("  help               Show this help message")
        println()
        println("Options:")
        println("  --verbose, -v      Show detailed analysis")
        println("  --production-only, -p  Skip test files")
        println()
        println("Examples:")
        println("  CliTool scan ./src/main/kotlin")
        println("  CliTool scan ./src/main/resources --verbose")
        println("  CliTool scan . --production-only")
        println()
        println("Note: This is a lightweight CLI version.")
        println("For complete analysis with all advanced features, use: ./gradlew bootRun")
        println()
    }
}

data class ScanArgs(
    val path: String,
    val verbose: Boolean,
    val productionOnly: Boolean
)

data class Issue(
    val severity: String,
    val description: String,
    val filename: String
)