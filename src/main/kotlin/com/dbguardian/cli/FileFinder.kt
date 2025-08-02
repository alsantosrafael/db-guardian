package com.dbguardian.cli

import java.io.File

/**
 * Handles file discovery and filtering for CLI analysis
 * Separated from main CLI logic for better testability
 */
class FileFinder {
    
    fun findRelevantFiles(path: String, config: CliConfig): List<File> {
        val rootFile = File(path)
        if (!rootFile.exists()) {
            return emptyList()
        }
        
        val files = if (rootFile.isFile) {
            listOf(rootFile)
        } else {
            rootFile.walkTopDown()
                .filter { it.isFile }
                .filter { file -> isRelevantFile(file) }
                .filter { file -> shouldIncludeFile(file, config) }
                .take(100) // Limit for CLI performance
                .toList()
        }
        
        return files
    }
    
    private fun isRelevantFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        val name = file.name
        
        return ext in setOf("kt", "java", "sql") ||
               name.contains("Repository") ||
               name.contains("Entity") ||
               name.contains("migration") ||
               name.contains("schema")
    }
    
    private fun shouldIncludeFile(file: File, config: CliConfig): Boolean {
        if (!config.productionOnly) {
            return true
        }
        
        // Exclude test files in production-only mode
        return !file.path.contains("/test/") && 
               !file.name.endsWith("Test.kt") && 
               !file.name.endsWith("Test.java") &&
               !file.name.endsWith("Spec.kt")
    }
}