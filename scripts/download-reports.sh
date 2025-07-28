#!/bin/bash

# Script to download analysis reports from LocalStack S3
# Usage: ./download-reports.sh [output-directory]

OUTPUT_DIR=${1:-./reports}
LOCALSTACK_ENDPOINT="http://localhost:4566"
BUCKET="query-analyzer-reports"

echo "📊 Downloading Analysis Reports from LocalStack S3"
echo "=================================================="

# Create output directory
mkdir -p "$OUTPUT_DIR"

# List all objects in bucket
echo "🔍 Finding reports..."
OBJECTS=$(curl -s "$LOCALSTACK_ENDPOINT/$BUCKET" | grep -o '<Key>[^<]*</Key>' | sed 's/<Key>//g' | sed 's/<\/Key>//g')

if [ -z "$OBJECTS" ]; then
    echo "❌ No reports found in bucket"
    exit 1
fi

echo "📥 Downloading reports to: $OUTPUT_DIR"
echo ""

# Download each file
for object in $OBJECTS; do
    filename=$(basename "$object")
    local_path="$OUTPUT_DIR/$filename"
    
    echo "  📄 $filename"
    curl -s "$LOCALSTACK_ENDPOINT/$BUCKET/$object" > "$local_path"
    
    # Show file info
    if [[ "$filename" == *.json ]]; then
        ISSUES=$(cat "$local_path" | jq -r '.summary.totalIssues // 0' 2>/dev/null)
        echo "     → $ISSUES issues found"
    fi
done

echo ""
echo "✅ Downloaded reports to: $OUTPUT_DIR"
echo "📂 Files:"
ls -la "$OUTPUT_DIR"

echo ""
echo "🔗 View reports:"
echo "   JSON: cat $OUTPUT_DIR/*.json | jq ."
echo "   Markdown: cat $OUTPUT_DIR/*.md"