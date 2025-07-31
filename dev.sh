#!/bin/bash

# DB Guardian - Complete Development Setup
# Starts services, creates S3 bucket, and runs the application

set -e

echo "🚀 Starting DB Guardian development environment..."

# Check prerequisites
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
    echo "❌ AWS CLI is not installed. Install with: brew install awscli"
    exit 1
fi

# Start services
echo "📦 Starting PostgreSQL and LocalStack..."
docker-compose up postgres localstack -d

# Wait for services
echo "⏳ Waiting for services to be ready..."
sleep 10

# Wait for PostgreSQL
until docker-compose exec postgres pg_isready -U dbguardian >/dev/null 2>&1; do
    echo "   PostgreSQL starting..."
    sleep 2
done
echo "✅ PostgreSQL ready"

# Wait for LocalStack
until curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; do
    echo "   LocalStack starting..."
    sleep 2
done
echo "✅ LocalStack ready"

# Create S3 bucket (CRITICAL: prevents analysis failures!)
echo "🪣 Creating S3 bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://dbguardian-reports --region us-east-1 >/dev/null 2>&1 || echo "   Bucket exists"

# Verify bucket
if aws --endpoint-url=http://localhost:4566 s3 ls s3://dbguardian-reports >/dev/null 2>&1; then
    echo "✅ S3 bucket ready"
else
    echo "❌ S3 bucket failed - analysis will not work!"
    exit 1
fi

echo ""
echo "🎯 Starting DB Guardian application..."
echo "   • Database: localhost:5432"
echo "   • S3: localhost:4566"
echo "   • API: will be localhost:8080"
echo ""

# Run application
export SPRING_PROFILES_ACTIVE=local
export S3_ENDPOINT=http://localhost:4566
./gradlew bootRun