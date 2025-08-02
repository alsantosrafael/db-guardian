import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Versão simplificada - será atualizada pelo CI
val appVersion = "0.4.2"

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.spring") version "1.9.20"
    kotlin("plugin.jpa") version "1.9.20"
    application
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

// Specify main class
springBoot {
    mainClass.set("com.dbguardian.DbGuardianApplicationKt")
}

// For CLI usage
application {
    mainClass.set("com.dbguardian.cli.CliTool")
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

// Create CLI-only fat JAR (standalone executable)
tasks.register<Jar>("cliFatJar") {
    group = "distribution"
    description = "Creates a standalone executable CLI JAR with all dependencies"
    archiveClassifier.set("cli")
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/LICENSE*")
        exclude("META-INF/NOTICE*")
    }
    
    manifest {
        attributes["Main-Class"] = "com.dbguardian.cli.CliTool"
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] = project.version
        attributes["Built-By"] = System.getProperty("user.name")
        attributes["Built-Date"] = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Create distribution scripts
tasks.register("createScripts") {
    group = "distribution"
    description = "Creates shell scripts for CLI distribution"
    
    doLast {
        val scriptsDir = file("build/scripts")
        scriptsDir.mkdirs()
        
        // Unix/macOS script
        val unixScript = scriptsDir.resolve("db-guardian")
        unixScript.writeText("""#!/bin/bash
# DB Guardian CLI Launcher
# Automatically detects if running from installation or development

SCRIPT_DIR="${'$'}( cd "${'$'}( dirname "${'$'}{BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Check if we're in a development environment (has gradlew)
if [ -f "${'$'}SCRIPT_DIR/../gradlew" ]; then
    # Development mode - use gradlew
    cd "${'$'}SCRIPT_DIR/.."
    ./gradlew -q run --args="${'$'}*"
elif [ -f "${'$'}SCRIPT_DIR/db-guardian.jar" ]; then
    # Distribution mode - use JAR
    java -jar "${'$'}SCRIPT_DIR/db-guardian.jar" ${'$'}*
else
    echo "❌ Error: Could not find db-guardian.jar or gradlew"
    echo "Make sure you're running from the correct directory"
    exit 1
fi
""")
        unixScript.setExecutable(true)
        
        // Install script
        val installScript = scriptsDir.resolve("install.sh")
        installScript.writeText("""#!/bin/bash
# DB Guardian Installation Script

set -e

# Default installation directory
INSTALL_DIR="/usr/local/bin"
JAR_DIR="/usr/local/lib/db-guardian"

# Parse arguments
while [[ ${'$'}# -gt 0 ]]; do
    case ${'$'}1 in
        --install-dir)
            INSTALL_DIR="${'$'}2"
            shift 2
            ;;
        --jar-dir)
            JAR_DIR="${'$'}2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: ${'$'}0 [--install-dir DIR] [--jar-dir DIR]"
            echo "  --install-dir DIR  Directory for executable script (default: /usr/local/bin)"
            echo "  --jar-dir DIR      Directory for JAR file (default: /usr/local/lib/db-guardian)"
            exit 0
            ;;
        *)
            echo "Unknown option: ${'$'}1"
            exit 1
            ;;
    esac
done

echo "🛡️  Installing DB Guardian CLI..."

# Create directories
sudo mkdir -p "${'$'}JAR_DIR"
sudo mkdir -p "${'$'}INSTALL_DIR"

# Copy JAR
if [ -f "db-guardian.jar" ]; then
    sudo cp db-guardian.jar "${'$'}JAR_DIR/"
    echo "✅ Copied JAR to ${'$'}JAR_DIR"
else
    echo "❌ Error: db-guardian.jar not found in current directory"
    exit 1
fi

# Create launcher script
sudo tee "${'$'}INSTALL_DIR/db-guardian" > /dev/null << EOF
#!/bin/bash
java -jar "${'$'}JAR_DIR/db-guardian.jar" \${'$'}*
EOF

sudo chmod +x "${'$'}INSTALL_DIR/db-guardian"

echo "✅ Created launcher script at ${'$'}INSTALL_DIR/db-guardian"
echo "🎉 Installation complete!"
echo ""
echo "Usage:"
echo "  db-guardian scan ./src --verbose"
echo "  db-guardian help"
""")
        installScript.setExecutable(true)
        
        println("Created scripts in build/scripts/")
    }
}

// Create distribution package
tasks.register<Zip>("createDistribution") {
    group = "distribution"
    description = "Creates a distribution package with CLI and scripts"
    archiveBaseName.set("db-guardian")
    archiveVersion.set(project.version.toString())
    
    from("build/libs") {
        include("*-cli.jar")
        rename(".*-cli.jar", "db-guardian.jar")
    }
    
    from("build/scripts") {
        include("db-guardian")
        include("install.sh")
    }
    
    from(".") {
        include("README.md")
        include("LICENSE")
    }
    
    into("db-guardian-${'$'}{project.version}")
    
    dependsOn("cliFatJar", "createScripts")
}

// Create Homebrew formula
tasks.register("createHomebrewFormula") {
    group = "distribution"
    description = "Creates a Homebrew formula for macOS distribution"
    
    doLast {
        val formulaDir = file("build/homebrew")
        formulaDir.mkdirs()
        
        val formula = formulaDir.resolve("db-guardian.rb")
        formula.writeText("""class DbGuardian < Formula
  desc "Spring Boot SQL security linter and analyzer"
  homepage "https://github.com/your-username/db-guardian"
  url "https://github.com/your-username/db-guardian/releases/download/v${'$'}{project.version}/db-guardian-${'$'}{project.version}.zip"
  sha256 "REPLACE_WITH_ACTUAL_SHA256"
  license "MIT"

  depends_on "openjdk@21"

  def install
    libexec.install "db-guardian.jar"
    bin.write_jar_script libexec/"db-guardian.jar", "db-guardian", java_version: "21"
  end

  test do
    system bin/"db-guardian", "help"
  end
end
""")
        
        println("Created Homebrew formula at build/homebrew/db-guardian.rb")
        println("⚠️  Remember to update the URL and SHA256 hash for actual release")
    }
}

// Convenience task to build everything
tasks.register("buildCli") {
    group = "distribution"
    description = "Builds CLI with all distribution artifacts"
    
    dependsOn("cliFatJar", "createDistribution", "createHomebrewFormula")
    
    doLast {
        println("")
        println("🎉 CLI build complete!")
        println("📦 Distribution ZIP: build/distributions/db-guardian-${'$'}{project.version}.zip")
        println("☕ Standalone JAR: build/libs/db-guardian-${'$'}{project.version}-cli.jar")
        println("🍺 Homebrew formula: build/homebrew/db-guardian.rb")
        println("")
        println("To test locally:")
        println("  java -jar build/libs/db-guardian-${'$'}{project.version}-cli.jar scan ./src")
        println("")
    }
}
