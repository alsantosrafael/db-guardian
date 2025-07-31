# Query Analyzer

A **SQL static analysis system** for JVM environments that scans repositories for SQL and database-related files, analyzes them using pattern-based detection, and generates comprehensive reports stored in S3.

## 🔍 **How It Works**

Query Analyzer performs intelligent static analysis of SQL code found in JVM projects (Spring Data JPA, Hibernate, JDBC), detecting common SQL issues and anti-patterns through rule-based analysis.

**Current Approach**: Pattern-based SQL analysis with plans for RAG enhancement. The system provides immediate value through proven static analysis techniques while being designed for future AI augmentation.

### **Current Analysis Flow**

1. **Repository Scanning** → System scans for SQL/database files (SQL, Kotlin, Java, Scala, Groovy)
2. **SQL Extraction** → Smart extraction using language-specific patterns  
3. **Pattern Analysis** → Rule-based detection of common SQL issues and anti-patterns
4. **Issue Classification** → Issues categorized by severity (CRITICAL, WARNING, INFO)
5. **Report Generation** → Rich markdown and JSON reports stored in S3
6. **API Access** → RESTful endpoints for analysis requests and report retrieval

**🚀 Planned RAG Enhancement**: Vector embeddings and AI-powered suggestions coming in future iterations.

### **Architecture Highlights**

- **Clean separation of concerns** - API, analysis, persistence, and reporting layers
- **Unified configuration** - Single application.yml with profile-based overrides
- **Local development ready** - Docker Compose with LocalStack S3 emulation

## ✨ **Key Features**

### 🔍 **Pattern-Based Analysis**
- **Rule-based detection** for common SQL anti-patterns and issues
- **JVM-focused** analysis (Spring Data JPA, Hibernate, JDBC)
- **Multiple severity levels** (CRITICAL, WARNING, INFO)
- **Comprehensive pattern matching** for SELECT *, missing WHERE clauses, etc.

### 📁 **JVM-Focused File Scanning**
- **JVM languages**: Kotlin, Java, Scala, Groovy
- **SQL files**: .sql, .ddl, .dml, .pgsql, .mysql
- **Intelligent extraction**: Detects SQL in code strings, annotations, and configuration
- **Auto-detection**: Finds migration, schema, entity, repository, and DAO files

### 🔒 **Production-Ready Security**
- **Unified configuration**: Environment-based settings with Spring profiles
- **LocalStack integration**: Local S3 development environment
- **Secure defaults**: Proper database connection pooling and error handling

### 📊 **Comprehensive Reporting**
- **Rich markdown reports**: Professional formatting with emojis and syntax highlighting
- **JSON metadata**: Machine-readable analysis results
- **S3 organization**: `reports/YYYY/MM/DD/runId.{md|json}` structure
- **Issue grouping**: By severity, technique, and file location

### 🚀 **Production-Ready API**
- **Async processing**: Background analysis with status tracking
- **REST endpoints**: `/api/analyze`, `/api/report/{runId}`, `/actuator/health`
- **Spring Boot**: Modern Spring Boot 3.x with Kotlin
- **Clean architecture**: Domain-driven design with separation of concerns

## Quick Start

### Prerequisites
- JDK 21+
- Docker & Docker Compose (for local development)
- PostgreSQL database
- S3-compatible storage (AWS S3 or LocalStack)

### Configuration

**🎯 Unified Configuration System**

All environment configuration is managed through a single `application.yml` with Spring profiles:

- **`local` profile** - LocalStack S3, PostgreSQL via Docker
- **`docker` profile** - Container-optimized settings
- **`production` profile** - AWS S3, production database

**Key Configuration Properties:**
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/queryanalyzer}
    username: ${DB_USERNAME:queryanalyzer}
    password: ${DB_PASSWORD:password}

aws:
  s3:
    endpoint: ${AWS_S3_ENDPOINT:}  # LocalStack for local development
    region: ${AWS_REGION:us-east-1}

app:
  reports:
    s3:
      bucket: ${REPORTS_S3_BUCKET:dbguardian-reports}
```

**Benefits of This Approach:**
- ✅ **No `.env` files needed** - Pure Spring Boot configuration
- ✅ **Profile-specific settings** - Different configs for local/prod
- ✅ **Environment variable override** - Secure credential injection
- ✅ **IDE integration** - IntelliJ/VS Code understand Spring profiles

### Local Development (Recommended)

**🚀 One-Command Setup:**
```bash
# Start everything: services + S3 bucket + application
./dev.sh
```

**🔧 Manual Setup:**
```bash
# Start required services (PostgreSQL, LocalStack S3)
docker-compose up -d postgres localstack

# Create S3 bucket (CRITICAL - prevents analysis failures!)
aws --endpoint-url=http://localhost:4566 s3 mb s3://dbguardian-reports

# Run the application
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

**🔍 Test the System:**
```bash
# Health check
curl http://localhost:8080/actuator/health

# Run analysis on test data (requires authentication)
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -H "x-api-token: dev-token-change-in-production" \
  -d '{
    "mode": "STATIC",
    "source": "src/test/resources/test-data-2",
    "config": {
      "dialect": "POSTGRESQL"
    }
  }'

# Get report (use runId from previous response)
curl "http://localhost:8080/api/report/{runId}"

# Download reports locally
./scripts/download-reports.sh
./scripts/view-reports.sh
```

**🐛 Troubleshooting:**
```bash
# Check all services status
docker-compose ps

# View service logs
docker-compose logs postgres localstack

# Reset environment (fresh start)
docker-compose down --volumes
```

### Production Setup

1. **Configure Environment Variables**
```bash
export SPRING_PROFILES_ACTIVE=production
export REPORTS_S3_BUCKET=your-s3-bucket
export AWS_REGION=us-east-1
export DB_USERNAME=queryanalyzer
export DB_PASSWORD=your_secure_password
```

*Note: All configuration is managed through Spring Boot's `application.yml` files. No `.env` files needed!*

2. **Start PostgreSQL**
```bash
docker run --name queryanalyzer-db \
  -e POSTGRES_DB=queryanalyzer \
  -e POSTGRES_USER=queryanalyzer \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 -d postgres:15
```

3. **Run Application**
```bash
./gradlew bootRun
```

### API Usage

**Start Repository Analysis**
```bash
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "STATIC",
    "source": "src/main/kotlin",
    "config": {
      "dialect": "POSTGRESQL"
    }
  }'
```

**Get Report**
```bash
curl "http://localhost:8080/api/report/{runId}"
# Returns: {"runId": "...", "status": "completed", "critical": 0, "warnings": 2}
```

## 🏗️ **System Architecture**

Query Analyzer uses a **clean, modular architecture** built with Spring Boot:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Server    │    │ Static Analysis │    │    Reporting    │
│                 │    │                 │    │                 │
│ • REST endpoints│◄──►│ • File scanning │◄──►│ • S3 storage    │
│ • Request DTOs  │    │ • SQL extraction│    │ • Markdown gen. │
│ • Error handling│    │ • Pattern match │    │ • JSON metadata │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
         ┌─────────────────────────────────────────────────┐
         │              Core Analysis                      │
         │                                                 │
         │  • Domain model (AnalysisRun, AnalysisConfig)  │
         │  • Business logic and orchestration             │
         │  • Database persistence with Flyway            │
         └─────────────────────────────────────────────────┘
```

### **Module Responsibilities**

| Package | Purpose | Key Components |
|---------|---------|----------------|
| **apiserver** | HTTP interface | REST controllers, DTOs, exception handling |
| **staticanalysis** | Code scanning | JVM file scanning, SQL extraction, pattern matching |
| **coreanalysis** | Business logic | Domain model, analysis orchestration, persistence |
| **reporting** | Output generation | S3 storage, markdown formatting, report generation |

### **Data Flow**

1. **API Request** → Analysis configuration submitted via REST
2. **File Discovery** → JVM-focused scanner finds SQL and code files
3. **SQL Extraction** → Language-specific patterns extract queries from code
4. **Pattern Analysis** → Rule-based detection of SQL issues and anti-patterns
5. **Issue Classification** → Results categorized by severity (CRITICAL, WARNING, INFO)
6. **Report Generation** → Rich markdown + JSON reports stored in S3
7. **API Response** → Analysis results returned via REST endpoints

## Current Analysis Techniques

The system includes built-in analysis techniques:

| Technique | Severity | Description |
|-----------|----------|-------------|
| `AVOID_SELECT_STAR` | WARNING | Detects SELECT * usage that can impact performance |
| `REQUIRE_WHERE_CLAUSE` | CRITICAL | Missing WHERE in UPDATE/DELETE operations |

**🚀 More techniques planned**: Additional pattern detection rules will be added in future releases, with RAG-powered enhancement coming as the learning module progresses.

## Sample Report Output

The system generates rich markdown reports like this:

```markdown
# SQL Analysis Report

**Analysis ID:** `550e8400-e29b-41d4-a716-446655440000`
**Mode:** STATIC
**Status:** COMPLETED

## Summary
- **Total Issues:** 5
- **Critical:** 3
- **Warnings:** 2
- **Files Analyzed:** 1
- **Queries Analyzed:** 7

## 🚨 Critical Issues

### REQUIRE_WHERE_CLAUSE
**Count:** 1
**Description:** DELETE without WHERE clause can remove all rows

- src/main/kotlin/SqlStaticAnalyzer.kt:1

## ⚠️ Warnings

### AVOID_SELECT_STAR
**Count:** 2
**Description:** Using SELECT * can lead to performance issues

- src/main/kotlin/SqlStaticAnalyzer.kt:1
```

## Database Schema

- `analysis_runs`: Stores analysis metadata, status, and S3 report references
- Clean migrations using Flyway for database versioning
- Minimal storage - detailed reports are in S3

## Supported File Types

The scanner detects SQL in these JVM-focused file types:
- **SQL Files**: `.sql`, `.ddl`, `.dml`, `.pgsql`, `.mysql`
- **JVM Code**: `.kt`, `.java`, `.scala`, `.groovy`
- **Config Files**: `.yml`, `.yaml`, `.xml`, `.properties`
- **Auto-detection**: Files with "migration", "schema", "entity", "repository" in name

## Testing & Quality

- **Comprehensive test suite**: Unit and integration tests
- **Load testing**: `./load-tests/run-load-test.sh` for professional performance testing
- **Smoke testing**: `./scripts/run-smoke-test.sh` for quick functionality verification
- **Local report access**: `./scripts/download-reports.sh` for easy report viewing

### Load Testing

**Run Professional Load Tests:**
```bash
# Prerequisites: Install k6 (https://k6.io/docs/getting-started/installation/)
# Make sure application is running first

# Run complete load testing suite
./load-tests/run-load-test.sh

# Run just the load test
./load-tests/run-load-test.sh load

# Quick k6 test without reports
k6 run load-tests/load-test.js
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes following the existing code patterns
4. Add tests for new functionality
5. Submit a pull request

## License

MIT License - see LICENSE file for details.