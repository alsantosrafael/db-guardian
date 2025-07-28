package com.queryanalyzer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@SpringBootApplication
@Modulithic
class QueryAnalyzerApplication

fun main(args: Array<String>) {
    runApplication<QueryAnalyzerApplication>(*args)
}