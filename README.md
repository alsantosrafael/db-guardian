# Query Analyzer

A **RAG-powered SQL analysis system** that scans repositories for SQL and database-related files, analyzes them using AI-enhanced vector embeddings, and generates comprehensive markdown reports stored in AWS S3.

## 🧠 **How It Works**

Query Analyzer uses **Retrieval-Augmented Generation (RAG)** to intelligently match SQL patterns against a knowledge base of best practices, providing contextual suggestions and confidence scoring for each detected issue.

**Key Innovation**: Instead of traditional rule-based analysis, this system uses vector embeddings to understand SQL patterns semantically, enabling more accurate and contextual analysis.

### **RAG Analysis Flow**

1. **Repository Scanning** → System scans for SQL/database files across 15+ file types
2. **SQL Extraction** → Smart extraction using language-specific patterns  
3. **Vector Embeddings** → Each SQL query is converted to semantic embeddings using MiniLM
4. **Similarity Search** → Queries are matched against knowledge base using vector similarity
5. **Confidence Scoring** → Each issue gets a confidence score (0.0-1.0) based on pattern match strength
6. **AI Enhancement** → High-confidence issues get OpenAI contextual suggestions
7. **Report Generation** → Rich markdown reports stored in S3 with pre-signed URLs

### **Security Architecture**

- **Pre-signed URLs with 15-minute TTL** - URLs generated on-demand, not stored
- **Minimal database storage** - Only metadata and S3 references
- **AWS best practices** - Proper S3 security model with temporary access

## ✨ **Key Features**

### 🧠 **RAG-Powered Intelligence**
- **Vector embeddings** using MiniLM for semantic SQL understanding
- **15+ analysis techniques** covering security, performance, and design patterns
- **OpenAI integration** for contextual suggestions (with local Ollama fallback)
- **Confidence scoring** (0.7 threshold) ensures high-quality issue detection

### 📁 **Smart Multi-Language Scanning**
- **15+ file types**: SQL, Kotlin, Java, Python, JavaScript, TypeScript, YAML, etc.
- **Intelligent extraction**: Detects SQL in code, configurations, comments, and strings
- **Auto-detection**: Finds migration, schema, entity, repository, and DAO files
- **Language-specific patterns**: Regex patterns optimized for each programming language

### 🔒 **Enterprise-Grade Security**
- **Pre-signed URLs**: 15-minute TTL, generated on-demand
- **Zero URL storage**: No static URLs in database
- **AWS best practices**: Proper IAM, S3 bucket policies
- **LGPD/GDPR ready**: PII detection and data minimization

### 📊 **Comprehensive Reporting**
- **Rich markdown reports**: Professional formatting with emojis and syntax highlighting
- **JSON metadata**: Machine-readable analysis results
- **S3 organization**: `reports/YYYY/MM/DD/runId.{md|json}` structure
- **Issue grouping**: By severity, technique, and file location

### 🚀 **Production-Ready API**
- **Async processing**: Background analysis with status tracking
- **REST endpoints**: Simple `/api/analyze`, `/api/report/{runId}`
- **Event-driven**: Spring Modulith with domain events
- **Graceful degradation**: Fallback mechanisms for AI services

## Quick Start

### Prerequisites
- JDK 17+
- PostgreSQL database
- AWS S3 bucket for report storage
- OpenAI API key (optional, for enhanced suggestions)

### Configuration

**🎯 Centralized Configuration Strategy**

All environment configuration is managed through Spring Boot profiles:

- **`application.yml`** - Base configuration with environment variable placeholders
- **`application-local.yml`** - Local development (LocalStack S3, Ollama LLM)  
- **`application-prod.yml`** - Production settings (real AWS S3, OpenAI)

**Key Configuration Properties:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/queryanalyzer
    username: ${DB_USERNAME:queryanalyzer}
    password: ${DB_PASSWORD:password}

app:
  openai:
    api-key: ${OPENAI_API_KEY:}
  reports:
    s3:
      bucket: ${REPORTS_S3_BUCKET:query-analyzer-reports}
  analysis:
    confidence-threshold: ${ANALYSIS_CONFIDENCE_THRESHOLD:0.7}
```

**Benefits of This Approach:**
- ✅ **No `.env` files needed** - Pure Spring Boot configuration
- ✅ **Profile-specific settings** - Different configs for local/prod
- ✅ **Environment variable override** - Secure credential injection
- ✅ **IDE integration** - IntelliJ/VS Code understand Spring profiles

### Local Development (Recommended)

**🚀 Quick Setup with Docker Compose:**
```bash
# Start all required services (PostgreSQL, LocalStack S3, Ollama)
docker-compose -f docker-compose.local.yml up -d

# Wait for services to be ready
sleep 10

# Run the application with local profile
./gradlew bootRun --args='--spring.profiles.active=local'
```

**🔍 Test the System:**
```bash
# Health check
curl http://localhost:8080/api/health

# Run analysis on test data
curl -X POST http://localhost:8080/api/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "static",
    "source": "repo",
    "config": {
      "dialect": "postgresql",
      "migrationPaths": ["src/test/resources/test-data"],
      "codePath": "src/main/kotlin"
    }
  }'

# Get report (use runId from previous response)
curl "http://localhost:8080/api/report/{runId}"
```

**🐛 Troubleshooting:**
```bash
# Check all services status
docker-compose -f docker-compose.local.yml ps

# View application logs
docker-compose -f docker-compose.local.yml logs -f

# Reset environment
docker-compose -f docker-compose.local.yml down -v
```

### Production Setup

1. **Configure Environment Variables**
```bash
export OPENAI_API_KEY=your_openai_key
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
    "mode": "static",
    "source": "repo",
    "config": {
      "dialect": "postgresql",
      "migrationPaths": ["src/main/resources/db/migration"],
      "codePath": "src/main/kotlin"
    }
  }'
```

**Get Report URL**
```bash
curl "http://localhost:8080/api/report/{runId}"
# Returns: {"reportUrl": "https://s3.amazonaws.com/.../runId.md"}
```

## 🏗️ **System Architecture**

Query Analyzer uses a **modular, event-driven architecture** built with Spring Modulith:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   API Server    │    │  RAG Analysis   │    │ Knowledge Base  │
│                 │    │                 │    │                 │
│ • REST endpoints│◄──►│ • Vector search │◄──►│ • 15+ techniques│
│ • Request DTOs  │    │ • Confidence    │    │ • MiniLM embed. │
│ • Pre-signed URLs│   │ • OpenAI enhance│    │ • Pattern match │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Static Analysis │    │  Core Analysis  │    │    Reporting    │
│                 │    │                 │    │                 │
│ • File scanning │◄──►│ • Domain model  │◄──►│ • S3 storage    │
│ • SQL extraction│    │ • Event publish │    │ • Markdown gen. │
│ • Multi-language│    │ • Analysis runs │    │ • JSON metadata │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### **Module Responsibilities**

| Module | Purpose | Key Components |
|--------|---------|----------------|
| **api-server** | HTTP interface | REST controllers, DTOs, exception handling |
| **rag-analysis** | AI-powered analysis | Vector similarity, OpenAI integration, confidence scoring |
| **knowledge-base** | SQL expertise | 15+ analysis techniques, vector embeddings, pattern library |
| **static-analysis** | Code scanning | Multi-language extraction, file type detection |
| **core-analysis** | Business logic | Domain model, event publishing, analysis orchestration |
| **reporting** | Output generation | S3 storage, markdown formatting, pre-signed URLs |

### **Data Flow**

1. **API Request** → Analysis configuration submitted via REST
2. **File Discovery** → Multi-language scanner finds SQL files
3. **SQL Extraction** → Language-specific patterns extract queries
4. **Vector Embedding** → MiniLM converts SQL to semantic vectors
5. **RAG Matching** → Similarity search against knowledge base
6. **AI Enhancement** → OpenAI provides contextual suggestions
7. **Report Generation** → Rich markdown + JSON stored in S3
8. **URL Generation** → Pre-signed URLs (15-min TTL) returned to client

## RAG Analysis Techniques

The system includes 15+ built-in analysis techniques:

| Technique | Severity | Description |
|-----------|----------|-------------|
| `AVOID_SELECT_STAR` | WARNING | Detects SELECT * usage |
| `REQUIRE_WHERE_CLAUSE` | CRITICAL | Missing WHERE in UPDATE/DELETE |
| `PREVENT_CARTESIAN_JOINS` | CRITICAL | Joins without proper conditions |
| `PREVENT_SQL_INJECTION` | CRITICAL | Potential SQL injection patterns |
| `OPTIMIZE_PAGINATION` | WARNING | Large OFFSET values |
| `OPTIMIZE_SUBQUERIES` | INFO | Subqueries that could be JOINs |
| `DETECT_N_PLUS_ONE` | WARNING | Potential N+1 query problems |
| And 8 more... | | |

### **RAG Analysis Deep Dive**

#### **1. Vector Embedding Process**
```
SQL Query → Tokenization → MiniLM Model → 384-dim Vector → Similarity Index
```
- **MiniLM-L6-v2**: Fast, accurate sentence embeddings
- **Semantic understanding**: Captures meaning beyond regex patterns
- **Contextual matching**: Similar queries grouped automatically

#### **2. Knowledge Base Structure**
```kotlin
data class AnalysisContext(
    val queryPattern: String,      // Regex pattern for initial filtering
    val technique: String,         // Analysis technique identifier  
    val severity: String,          // CRITICAL, WARNING, INFO
    val description: String,       // What the issue is
    val suggestion: String,        // How to fix it
    val examples: List<String>,    // Before/after code examples
    val references: List<String>   // Documentation links
)
```

#### **3. Confidence Scoring Algorithm**
```
Base Score (0.5) + Pattern Match (0.3) + Known Anti-pattern (0.2) - Complexity Penalty (0.1)
```
- **0.7+ threshold**: Issues reported to user
- **0.8+ threshold**: Enhanced with OpenAI suggestions
- **Dynamic adjustment**: Based on query length and complexity

#### **4. AI Enhancement Pipeline**
```
High Confidence Issue → OpenAI GPT-4 → Contextual Suggestion → Fallback to Base
```
- **Graceful degradation**: Always falls back to knowledge base suggestions
- **Rate limiting**: Built-in error handling for API limits
- **Local option**: Ollama integration for offline usage

## Sample Report Output

The system generates rich markdown reports like this:

```markdown
# SQL Analysis Report

**Analysis ID:** `550e8400-e29b-41d4-a716-446655440000`
**Mode:** STATIC
**Status:** COMPLETED

## Summary
- **Total Issues:** 3
- **Critical:** 1
- **Warnings:** 2
- **Files Analyzed:** 8
- **Queries Analyzed:** 12

## 🚨 Critical Issues

### PREVENT_SQL_INJECTION
**Count:** 1
**Description:** Potential SQL injection vulnerability detected

- src/UserService.kt:45 (95% confidence)

## ⚠️ Warnings

### AVOID_SELECT_STAR
**Count:** 2
**Description:** Using SELECT * can lead to performance issues

- migration/V1__create_users.sql:12 (85% confidence)
- src/OrderRepository.kt:23 (78% confidence)
```

## Database Schema

- `analysis_runs`: Stores analysis metadata and S3 report URLs
- Database is minimal - main data is in S3 markdown reports

## Supported File Types

The scanner detects SQL in these file types:
- **SQL Files**: `.sql`, `.ddl`, `.dml`, `.pgsql`, `.mysql`
- **Code Files**: `.kt`, `.java`, `.py`, `.js`, `.ts`, `.rb`, `.go`, `.php`
- **Config Files**: `.yml`, `.yaml`, `.xml`, `.properties`, `.json`
- **Auto-detection**: Files with "migration", "schema", "entity", "repository" in name

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes following the existing code patterns
4. Add tests for new functionality
5. Submit a pull request

## License

MIT License - see LICENSE file for details.