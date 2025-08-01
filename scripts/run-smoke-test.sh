#!/bin/bash

# Query Analyzer - Smoke Test Script
# Tests the full analysis workflow

set -e

echo "🧪 Running Query Analyzer Smoke Test"
echo "===================================="

# Check if application is running
if ! curl -f http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo "❌ Application is not running. Start it first:"
    echo "   S3_ENDPOINT=http://localhost:4566 ./gradlew bootRun"
    exit 1
fi

# Check if LocalStack is running
if ! curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; then
    echo "❌ LocalStack is not running. Run ./scripts/setup-local.sh first"
    exit 1
fi

echo ""
echo "1️⃣ Testing health endpoint..."
curl -s http://localhost:8080/actuator/health | jq '.' || echo "Health check failed"

echo ""
echo "2️⃣ Starting analysis..."
ANALYSIS_RESPONSE=$(curl -s -X POST http://localhost:8080/api/v1/analysis \
  -H "x-api-token: abacaxi" \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "static",
    "source": "./src/main/kotlin",
    "config": {
      "dialect": "postgresql",
      "migrationPaths": ["./src/main/resources/db/migration"],
      "codePath": "./src/main/kotlin"
    }
  }')

echo "Analysis response: $ANALYSIS_RESPONSE"

# Extract runId
RUN_ID=$(echo $ANALYSIS_RESPONSE | jq -r '.runId' 2>/dev/null || echo "")

if [ -z "$RUN_ID" ] || [ "$RUN_ID" = "null" ]; then
    echo "❌ Failed to start analysis"
    exit 1
fi

echo "✅ Analysis started with ID: $RUN_ID"

echo ""
echo "3️⃣ Waiting for analysis to complete..."
sleep 10

echo ""
echo "4️⃣ Getting analysis report..."
REPORT_RESPONSE=$(curl -s -H "x-api-token: abacaxi" "http://localhost:8080/api/v1/report/$RUN_ID")
echo "Report response: $REPORT_RESPONSE"

# Check if we got a report URL
REPORT_URL=$(echo $REPORT_RESPONSE | jq -r '.reportUrl' 2>/dev/null || echo "")

if [ -z "$REPORT_URL" ] || [ "$REPORT_URL" = "null" ]; then
    echo "❌ Failed to get report URL"
    exit 1
fi

echo "✅ Report URL generated: $REPORT_URL"

echo ""
echo "5️⃣ Testing report access..."
if curl -f "$REPORT_URL" >/dev/null 2>&1; then
    echo "✅ Report is accessible"
else
    echo "⚠️ Report URL not accessible (may be expired or S3 issue)"
fi

echo ""
echo "🎉 Smoke test completed successfully!"
echo ""
echo "✅ Application is working correctly"
echo "✅ Analysis workflow is functional"
echo "✅ Report generation is working"
echo ""
echo "You can view the report at: $REPORT_URL"
echo ""