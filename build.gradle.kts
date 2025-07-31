import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Versão simplificada - será atualizada pelo CI
val appVersion = "0.2.0"

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
}

group = "com.queryanalyzer"
version = appVersion

java {
    sourceCompatibility = JavaVersion.VERSION_21
}


repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Spring Modulith
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-events-api")
    
    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    
    // SQL parsing (SQLGlot equivalent for JVM)
    implementation("com.github.jsqlparser:jsqlparser:4.9")
    
    // AWS SDK for S3
    implementation("software.amazon.awssdk:s3:2.21.0")
    implementation("software.amazon.awssdk:s3-transfer-manager:2.21.0")
    implementation("software.amazon.awssdk:core:2.21.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.h2database:h2")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.testcontainers:junit-jupiter")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:1.1.0")
        mavenBom("org.testcontainers:testcontainers-bom:1.19.3")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    
    // Exclude integration tests from default test task
    exclude("**/integration/**")
    exclude("**/security/SecurityConfigTest.class")
}

// Create separate task for integration tests
tasks.register<Test>("integrationTest") {
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    
    // Only run integration tests
    include("**/integration/**")
    include("**/security/SecurityConfigTest.class")
}
