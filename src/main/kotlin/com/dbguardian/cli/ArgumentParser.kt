package com.dbguardian.cli

/**
 * Handles command line argument parsing
 * Separated from main CLI for better testability and clarity
 */
class ArgumentParser {
    
    fun parseArgs(args: Array<String>): CliCommand {
        if (args.isEmpty()) {
            return CliCommand.Help
        }
        
        return when (args[0]) {
            "scan" -> {
                val scanArgs = parseScanArgs(args.drop(1))
                CliCommand.Scan(scanArgs)
            }
            "help", "--help", "-h" -> CliCommand.Help
            else -> CliCommand.Unknown(args[0])
        }
    }
    
    private fun parseScanArgs(args: List<String>): CliConfig {
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
        
        return CliConfig(path, verbose, productionOnly)
    }
}

sealed class CliCommand {
    data class Scan(val config: CliConfig) : CliCommand()
    object Help : CliCommand()
    data class Unknown(val command: String) : CliCommand()
}