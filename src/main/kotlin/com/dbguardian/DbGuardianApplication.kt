package com.dbguardian

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@SpringBootApplication
@Modulithic
class DbGuardianApplication

fun main(args: Array<String>) {
    runApplication<DbGuardianApplication>(*args)
}