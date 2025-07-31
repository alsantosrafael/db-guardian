#!/bin/bash

# Simple development setup script

set -e

echo "🚀 Starting DB Guardian development environment..."

# Start services
echo "📦 Starting PostgreSQL and LocalStack..."
docker-compose up postgres localstack -d

# Wait for services
echo "⏳ Waiting for services to be ready..."
sleep 10

# Create S3 bucket in LocalStack
echo "🪣 Creating S3 bucket..."
aws --endpoint-url=http://localhost:4566 s3 mb s3://dbguardian-reports --region us-east-1 || true

# Run application
echo "🎯 Starting application..."
export SPRING_PROFILES_ACTIVE=local
export S3_ENDPOINT=http://localhost:4566
./gradlew bootRun