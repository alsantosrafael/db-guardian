#!/bin/bash

# Query Analyzer - Health Check Script
# Checks all services and application health

set -e

echo "🏥 Query Analyzer Health Check"
echo "=============================="

# Check Docker services
echo ""
echo "📦 Checking Docker services..."
docker-compose -f docker-compose.local.yml ps

# Check individual services
echo ""
echo "🐘 PostgreSQL Health:"
if docker-compose -f docker-compose.local.yml exec postgres pg_isready -U queryanalyzer >/dev/null 2>&1; then
    echo "   ✅ PostgreSQL is healthy"
else
    echo "   ❌ PostgreSQL is not ready"
fi

echo ""
echo "☁️ LocalStack S3 Health:"
if curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; then
    echo "   ✅ LocalStack S3 is healthy"
    # Check if bucket exists
    if aws --endpoint-url=http://localhost:4566 s3 ls s3://query-analyzer-reports >/dev/null 2>&1; then
        echo "   ✅ S3 bucket exists"
    else
        echo "   ⚠️ S3 bucket missing - run setup-local.sh"
    fi
else
    echo "   ❌ LocalStack S3 is not ready"
fi

echo ""
echo "🤖 Ollama Health:"
if curl -f http://localhost:11434/api/tags >/dev/null 2>&1; then
    echo "   ✅ Ollama is healthy"
else
    echo "   ⚠️ Ollama is not ready (may take time to start)"
fi

# Check application health
echo ""
echo "🚀 Application Health:"
if curl -f http://localhost:8080/api/health >/dev/null 2>&1; then
    echo "   ✅ Query Analyzer application is healthy"
    echo "   📊 Application response:"
    curl -s http://localhost:8080/api/health | jq '.' 2>/dev/null || curl -s http://localhost:8080/api/health
else
    echo "   ❌ Query Analyzer application is not responding"
    echo "   💡 Try: ./gradlew bootRun --args='--spring.profiles.active=local'"
fi

echo ""
echo "🎯 Summary:"
echo "   If all services show ✅, you're ready to develop!"
echo "   If any show ❌, run: ./scripts/setup-local.sh"
echo ""