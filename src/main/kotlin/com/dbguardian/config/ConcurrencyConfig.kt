package com.dbguardian.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
@EnableAsync
class ConcurrencyConfig {
    @Bean("ioExecutor")
    fun ioExecutor(): Executor = Executors.newVirtualThreadPerTaskExecutor()

    @Bean("cpuExecutor")
    fun cpuExecutor(): Executor {
        val threads = Runtime.getRuntime().availableProcessors()
        return Executors.newFixedThreadPool(threads)

    }
}