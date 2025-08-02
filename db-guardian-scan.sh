#!/bin/bash

# DB Guardian - Enhanced SQL Linter for Spring Boot
# Usage: ./db-guardian-scan.sh [path] [--verbose] [--production-only]

# Parse arguments
SCAN_PATH="."
VERBOSE=false
PRODUCTION_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --production-only|-p)
            PRODUCTION_ONLY=true
            shift
            ;;
        --help|-h)
            echo "🛡️  DB Guardian - Spring Boot SQL Linter"
            echo "Usage: $0 [path] [options]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v        Show detailed analysis"
            echo "  --production-only, -p Skip test files"
            echo "  --help, -h           Show this help"
            echo ""
            echo "Examples:"
            echo "  $0 ./src/main/kotlin"
            echo "  $0 . --verbose"
            echo "  $0 ./src --production-only"
            exit 0
            ;;
        *)
            SCAN_PATH="$1"
            shift
            ;;
    esac
done

echo "🛡️  DB Guardian - Spring Boot SQL Linter v2.1"
echo "==============================================="
echo ""
echo "🔍 Scanning: $SCAN_PATH"
if [ "$PRODUCTION_ONLY" = true ]; then
    echo "📋 Mode: Production only (excluding test files)"
fi
echo ""

# Check if we can use the main Kotlin CLI
if [ -f "./gradlew" ] && [ -f "src/main/kotlin/com/dbguardian/cli/CliTool.kt" ]; then
    echo "🚀 Using DB Guardian CliTool..."
    echo ""
    
    # Build if needed
    if [ ! -d "build/classes" ]; then
        echo "🔨 Building project..."
        ./gradlew compileKotlin > /dev/null 2>&1
    fi
    
    # Prepare arguments for CliTool
    CLI_ARGS="scan $SCAN_PATH"
    if [ "$VERBOSE" = true ]; then
        CLI_ARGS="$CLI_ARGS --verbose"
    fi
    if [ "$PRODUCTION_ONLY" = true ]; then
        CLI_ARGS="$CLI_ARGS --production-only"
    fi
    
    # Run CliTool using gradle application plugin
    ./gradlew -q run --args="$CLI_ARGS" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        exit 0
    fi
    
    echo "⚠️  CliTool failed, falling back to basic bash analysis..."
    echo ""
fi

# Fallback: Basic bash analysis (simplified version)
echo "📁 Running fallback bash analysis..."
echo ""

# Find relevant files with better filtering
if [ "$PRODUCTION_ONLY" = true ]; then
    FILES=$(find "$SCAN_PATH" -type f \( -name "*.kt" -o -name "*.java" -o -name "*.sql" \) 2>/dev/null | \
            grep -v "/test/" | grep -v "Test\." | head -50)
else
    FILES=$(find "$SCAN_PATH" -type f \( -name "*.kt" -o -name "*.java" -o -name "*.sql" \) 2>/dev/null | head -50)
fi

if [ -z "$FILES" ]; then
    echo "❌ No relevant files found in $SCAN_PATH"
    exit 1
fi

FILE_COUNT=$(echo "$FILES" | wc -l)
ISSUE_COUNT=0

echo "📁 Files to analyze: $FILE_COUNT"
echo ""

# Quick basic analysis (simplified rules)
for file in $FILES; do
    filename=$(basename "$file")
    
    if [ "$VERBOSE" = true ]; then
        echo "📄 Processing: $filename"
    fi
    
    # Basic SQL injection check
    if grep -q -E "\+.*SELECT|SELECT.*\+" "$file" 2>/dev/null; then
        if [[ ! "$file" =~ /test/ ]]; then
            echo "🚨 $filename: Potential SQL injection (string concatenation)"
            ISSUE_COUNT=$((ISSUE_COUNT + 1))
        fi
    fi
    
    # Basic UPDATE/DELETE without WHERE
    if grep -q -iE "(UPDATE|DELETE)" "$file" 2>/dev/null && ! grep -q -i "WHERE" "$file" 2>/dev/null; then
        echo "🚨 $filename: UPDATE/DELETE without WHERE clause"
        ISSUE_COUNT=$((ISSUE_COUNT + 1))
    fi
done

echo ""
echo "📊 Basic Analysis Results"
echo "========================="
echo "📁 Files analyzed: $FILE_COUNT"
echo "🚨 Issues found: $ISSUE_COUNT"

if [ $ISSUE_COUNT -eq 0 ]; then
    echo ""
    echo "✅ No critical issues found in basic analysis."
else
    echo ""
    echo "⚠️  Issues detected! For complete analysis, ensure Kotlin CLI is working."
fi

echo ""
echo "💡 To get full analysis with all rules, run: ./gradlew run --args='scan $SCAN_PATH'"
echo "💡 Or use CliTool directly: ./gradlew run --args='scan $SCAN_PATH --verbose'"
echo ""