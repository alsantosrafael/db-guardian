#!/bin/bash

# Query Analyzer - Local Development Setup
# Starts PostgreSQL, LocalStack S3, and Ollama for local development

set -e

echo "🚀 Setting up Query Analyzer local development environment..."

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

# Start services with docker-compose
echo "📦 Starting PostgreSQL, LocalStack S3, and Ollama..."
docker-compose -f docker-compose.local.yml up -d

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 10

# Check PostgreSQL
echo "🐘 Checking PostgreSQL..."
until docker-compose -f docker-compose.local.yml exec postgres pg_isready -U queryanalyzer >/dev/null 2>&1; do
    echo "   PostgreSQL is starting up..."
    sleep 2
done
echo "✅ PostgreSQL is ready"

# Check LocalStack S3
echo "☁️ Checking LocalStack S3..."
until curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; do
    echo "   LocalStack is starting up..."
    sleep 2
done
echo "✅ LocalStack S3 is ready"

# Create S3 bucket for reports
echo "🪣 Creating S3 bucket for reports..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://query-analyzer-reports >/dev/null 2>&1 || echo "   Bucket already exists"

# Check Ollama (optional - may take longer to start)
echo "🤖 Checking Ollama..."
if curl -f http://localhost:11434/api/tags >/dev/null 2>&1; then
    echo "✅ Ollama is ready"
else
    echo "⚠️ Ollama is starting (may take a few minutes)..."
    echo "   You can proceed with development, Ollama will be available soon"
fi

echo ""
echo "🎉 Local development environment is ready!"
echo ""
echo "Next steps:"
echo "   1. Run the application: ./gradlew bootRun --args='--spring.profiles.active=local'"
echo "   2. Test health: curl http://localhost:8080/api/health"
echo "   3. Run smoke test: ./scripts/run-smoke-test.sh"
echo ""
echo "Services running:"
echo "   • PostgreSQL: localhost:5432"
echo "   • LocalStack S3: localhost:4566"
echo "   • Ollama: localhost:11434"
echo ""