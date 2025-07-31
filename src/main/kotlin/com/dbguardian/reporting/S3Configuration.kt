package com.dbguardian.reporting

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
    fun s3Client(
        @Value("\${storage.s3.endpoint:}") endpoint: String,
        @Value("\${storage.s3.region:us-east-1}") region: String,
        @Value("\${AWS_ACCESS_KEY_ID:}") accessKey: String,
        @Value("\${AWS_SECRET_ACCESS_KEY:}") secretKey: String
    ): S3Client {
        val clientBuilder = S3Client.builder()
            .region(Region.of(region))
        
        // If endpoint is specified (LocalStack/MinIO), use custom configuration
        if (endpoint.isNotEmpty()) {
            val credentials = AwsBasicCredentials.create(accessKey.ifEmpty { "test" }, secretKey.ifEmpty { "test" })
            clientBuilder
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build()
                )
        } else {
            // Production AWS - use default credentials chain or provided keys
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
    fun s3Presigner(
        @Value("\${storage.s3.endpoint:}") endpoint: String,
        @Value("\${storage.s3.region:us-east-1}") region: String,
        @Value("\${AWS_ACCESS_KEY_ID:}") accessKey: String,
        @Value("\${AWS_SECRET_ACCESS_KEY:}") secretKey: String
    ): S3Presigner {
        val presignerBuilder = S3Presigner.builder()
            .region(Region.of(region))
            
        // If endpoint is specified (LocalStack/MinIO), use custom configuration  
        if (endpoint.isNotEmpty()) {
            val credentials = AwsBasicCredentials.create(accessKey.ifEmpty { "test" }, secretKey.ifEmpty { "test" })
            presignerBuilder
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
        } else {
            // Production AWS - use default credentials chain or provided keys
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