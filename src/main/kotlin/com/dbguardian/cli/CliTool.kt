package com.dbguardian.cli

/**
 * CLI Tool - Entry point for DB Guardian command line interface
 * Lightweight version that uses basic analysis rules without Spring dependencies
 * For full analysis with all advanced features, use the Spring Boot application
 */
object CliTool {
    
    private val argumentParser = ArgumentParser()
    private val fileFinder = FileFinder()
    private val analyzer = CliAnalyzer()
    private val formatter = ResultFormatter()
    
    @JvmStatic
    fun main(args: Array<String>) {
        run(args)
    }
    
    fun run(args: Array<String>) {
        when (val command = argumentParser.parseArgs(args)) {
            is CliCommand.Scan -> scan(command.config)
            is CliCommand.Help -> formatter.printUsage()
            is CliCommand.Unknown -> {
                println("❌ Unknown command: ${command.command}")
                formatter.printUsage()
            }
        }
    }
    
    private fun scan(config: CliConfig) {
        formatter.printScanHeader(config)
        
        val startTime = System.currentTimeMillis()
        
        // Find relevant files
        val files = fileFinder.findRelevantFiles(config.path, config)
        if (files.isEmpty()) {
            println("❌ No relevant files found in ${config.path}")
            return
        }
        
        println("📁 Files to analyze: ${files.size}")
        println()
        
        // Analyze files
        val result = analyzer.analyzeFiles(files, config)
        val duration = System.currentTimeMillis() - startTime
        
        // Print results
        formatter.printResults(result, duration)
    }
}