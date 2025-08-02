#!/bin/bash

# DB Guardian - Universal Runner
# Supports both API server and CLI modes

set -e

# Default mode
MODE="api"
CLI_ARGS=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --mode)
            MODE="$2"
            shift 2
            ;;
        --cli-args)
            CLI_ARGS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [--mode api|cli] [--cli-args \"args\"]"
            echo ""
            echo "Modes:"
            echo "  api  - Start Spring Boot API server (default)"
            echo "  cli  - Run CLI mode"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Start API server"
            echo "  $0 --mode api                         # Start API server"
            echo "  $0 --mode cli --cli-args \"scan ./src --verbose\""
            echo "  $0 --mode cli --cli-args \"help\""
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

case $MODE in
    api)
        echo "🚀 Starting DB Guardian API server..."
        
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
        aws --endpoint-url=http://localhost:4566 s3 mb s3://dbguardian --region us-east-1 >/dev/null 2>&1 || echo "   Bucket exists"
        
        # Verify bucket
        if aws --endpoint-url=http://localhost:4566 s3 ls s3://dbguardian >/dev/null 2>&1; then
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
        echo "💡 Press Ctrl+C to stop everything cleanly"
        echo ""
        
        # Set environment for local development
        export SPRING_PROFILES_ACTIVE=local
        export S3_ENDPOINT=http://localhost:4566
        
        # Use trap to cleanup on exit
        cleanup() {
            echo ""
            echo "🛑 Stopping services..."
            docker-compose down
            echo "✅ Cleanup complete"
            exit 0
        }
        trap cleanup SIGINT SIGTERM
        
        ./gradlew bootRun --console=plain --no-daemon
        ;;
    cli)
        echo "🛡️  Running DB Guardian CLI..."
        if [ -z "$CLI_ARGS" ]; then
            echo "   No arguments provided, showing help:"
            echo ""
            ./gradlew run --args="help" --console=plain --no-daemon
        else
            echo "   Arguments: $CLI_ARGS"
            echo ""
            ./gradlew run --args="$CLI_ARGS" --console=plain --no-daemon
        fi
        ;;
    *)
        echo "❌ Invalid mode: $MODE"
        echo "Valid modes: api, cli"
        exit 1
        ;;
esac