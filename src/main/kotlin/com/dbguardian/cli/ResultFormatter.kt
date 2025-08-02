package com.dbguardian.cli

/**
 * Handles output formatting and presentation for CLI results
 * Separated from analysis logic for cleaner code organization
 */
class ResultFormatter {
    
    fun printResults(result: AnalysisResult, duration: Long) {
        println()
        println("📊 Analysis Results")
        println("===================")
        println("⏱️  Analysis time: ${duration}ms")
        println("📁 Files analyzed: ${result.fileCount}")
        println("🔍 Queries found: ${result.queryCount}")
        
        val critical = result.issues.filter { it.severity == "CRITICAL" }
        val warnings = result.issues.filter { it.severity == "WARNING" }
        val info = result.issues.filter { it.severity == "INFO" }
        
        println("🚨 Critical issues: ${critical.size}")
        println("⚠️  Warning issues: ${warnings.size}")
        println("ℹ️  Info issues: ${info.size}")
        println("📋 Total issues: ${result.issues.size}")
        
        if (result.issues.isEmpty()) {
            printNoIssuesFound()
        } else {
            printIssueDetails(critical, warnings, info)
        }
        
        println()
    }
    
    private fun printNoIssuesFound() {
        println()
        println("✅ No issues found! Your SQL looks clean.")
    }
    
    private fun printIssueDetails(critical: List<Issue>, warnings: List<Issue>, info: List<Issue>) {
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
    
    fun printUsage() {
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
    
    fun printScanHeader(config: CliConfig) {
        println("🛡️  DB Guardian - Spring Boot SQL Linter")
        println("==========================================")
        println()
        println("🔍 Scanning: ${config.path}")
        
        if (config.productionOnly) {
            println("📋 Mode: Production only (excluding test files)")
        }
        if (config.verbose) {
            println("📋 Mode: Verbose output enabled")
        }
        println()
    }
}