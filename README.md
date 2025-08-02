# DB Guardian

SQL static analysis system for JVM environments. Scans repositories for SQL files and database code, analyzes them using pattern-based detection, and generates reports stored in S3.

## How It Works

1. **Repository Scanning** → Scans for SQL/database files (SQL, Kotlin, Java, Scala, Groovy)
2. **SQL Extraction** → Extracts SQL using language-specific patterns  
3. **Pattern Analysis** → Detects common SQL issues and anti-patterns
4. **Issue Classification** → Categorizes by severity (CRITICAL, WARNING, INFO)
5. **Report Generation** → Generates markdown and JSON reports stored in S3

## Key Features

- **Rule-based detection** for SQL anti-patterns (SELECT *, missing WHERE clauses, etc.)
- **JVM-focused** analysis (Spring Data JPA, Hibernate, JDBC)
- **Multiple file types**: SQL files, Kotlin, Java, Scala, Groovy
- **Async processing** with REST API endpoints
- **Rich reporting** with markdown and JSON formats
- **S3 storage** with organized report structure
- **Local development** with Docker Compose and LocalStack

## Quick Start

### Prerequisites
- JDK 21+
- Docker & Docker Compose (for local development)
- PostgreSQL database
- S3-compatible storage (AWS S3 or LocalStack)

### Configuration

Configuration is managed through `application.yml` with Spring profiles:

- **`local`** - LocalStack S3, PostgreSQL via Docker
- **`production`** - AWS S3, production database

Key environment variables:
```bash
SPRING_PROFILES_ACTIVE=local
DATABASE_URL=jdbc:postgresql://localhost:5432/dbguardian
S3_BUCKET=dbguardian
API_TOKEN=your-secure-token
```

### Local Development

**Quick Setup:**
```bash
# Start everything: services + S3 bucket + application
./dev.sh
```

**Manual Setup:**
```bash
# Start services
docker-compose up -d postgres localstack

# Create S3 bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://dbguardian

# Run application
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

**Test the System:**
```bash
# Health check
curl http://localhost:8080/actuator/health

# Run analysis
curl -X POST http://localhost:8080/api/v1/analysis \
  -H "Content-Type: application/json" \
  -H "x-api-token: abacaxi" \
  -d '{
    "mode": "STATIC",
    "source": "src/test/resources/test-data-2",
    "config": {
      "dialect": "POSTGRESQL"
    }
  }'
```

### Production Setup

```bash
# Set environment variables
export SPRING_PROFILES_ACTIVE=production
export S3_BUCKET=your-s3-bucket
export DATABASE_URL=jdbc:postgresql://your-db:5432/dbguardian
export API_TOKEN=your-secure-token

# Run application
./gradlew bootRun
```

### API Usage

**Start Analysis**
```bash
curl -X POST http://localhost:8080/api/v1/analysis \
  -H "Content-Type: application/json" \
  -H "x-api-token: your-token" \
  -d '{
    "mode": "STATIC",
    "source": "src/main/kotlin",
    "config": {
      "dialect": "POSTGRESQL"
    }
  }'
```

**Get Status**
```bash
curl "http://localhost:8080/api/v1/analysis/{runId}/status"
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
