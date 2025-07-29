package com.dbguardian.reporting

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class S3Configuration {
    
    @Bean
    @Primary
    fun reportStorage(
        @Value("\${app.reports.storage.type:local}") storageType: String,
        localReportStorage: LocalReportStorage,
        s3ReportStorage: S3ReportStorage?
    ): ReportStorage {
        return when (storageType.lowercase()) {
            "s3" -> s3ReportStorage ?: throw IllegalStateException("S3 storage requested but S3ReportStorage bean not available")
            "local" -> localReportStorage
            else -> localReportStorage // default to local
        }
    }
    
    @Bean
    @ConditionalOnProperty(name = ["app.reports.storage.type"], havingValue = "s3")
    fun s3Client(
        @Value("\${aws.s3.endpoint:}") endpoint: String,
        @Value("\${aws.region:us-east-1}") region: String,
        @Value("\${aws.access-key:}") accessKey: String,
        @Value("\${aws.secret-key:}") secretKey: String,
        @Value("\${aws.s3.path-style-access:false}") pathStyleAccess: Boolean
    ): S3Client {
        val clientBuilder = S3Client.builder()
            .region(Region.of(region))
        
        // If endpoint is specified (LocalStack/MinIO), use custom configuration
        if (endpoint.isNotEmpty()) {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            clientBuilder
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build()
                )
        } else {
            // Production AWS - use default credentials chain
            // Only create if we have access key or default credentials will work
            if (accessKey.isNotEmpty() && secretKey.isNotEmpty()) {
                val credentials = AwsBasicCredentials.create(accessKey, secretKey)
                clientBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials))
            } else {
                clientBuilder.credentialsProvider(DefaultCredentialsProvider.create())
            }
        }
        
        return clientBuilder.build()
    }
    
    @Bean
    @ConditionalOnProperty(name = ["app.reports.storage.type"], havingValue = "s3")
    fun s3Presigner(
        @Value("\${aws.s3.endpoint:}") endpoint: String,
        @Value("\${aws.region:us-east-1}") region: String,
        @Value("\${aws.access-key:}") accessKey: String,
        @Value("\${aws.secret-key:}") secretKey: String
    ): S3Presigner {
        val presignerBuilder = S3Presigner.builder()
            .region(Region.of(region))
            
        // If endpoint is specified (LocalStack/MinIO), use custom configuration  
        if (endpoint.isNotEmpty()) {
            val credentials = AwsBasicCredentials.create(accessKey, secretKey)
            presignerBuilder
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
        } else {
            // Production AWS - use default credentials chain
            // Only create if we have access key or default credentials will work
            if (accessKey.isNotEmpty() && secretKey.isNotEmpty()) {
                val credentials = AwsBasicCredentials.create(accessKey, secretKey)
                presignerBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials))
            } else {
                presignerBuilder.credentialsProvider(DefaultCredentialsProvider.create())
            }
        }
        
        return presignerBuilder.build()
    }
}