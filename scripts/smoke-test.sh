#!/bin/bash

# Database Guardian - Real Smoke Test Script
# This script performs a real analysis of problematic database code and generates comprehensive reports

set -e

echo "🚀 Database Guardian - Real Smoke Test"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Directories
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DATA_DIR="$PROJECT_ROOT/src/test/resources/test-data-2"
REPORTS_DIR="$PROJECT_ROOT/reports"

echo "📁 Project: $PROJECT_ROOT"
echo "🔍 Test Data: $TEST_DATA_DIR"
echo "📊 Reports: $REPORTS_DIR"
echo ""

# Verify test-data directory exists
if [ ! -d "$TEST_DATA_DIR" ]; then
    echo -e "${RED}❌ Test data directory not found: $TEST_DATA_DIR${NC}"
    exit 1
fi

# Create reports directory
mkdir -p "$REPORTS_DIR"

echo "🔍 ANALYZING PROBLEMATIC DATABASE CODE..."
echo "========================================"
echo ""

# Count files to be analyzed
TOTAL_FILES=$(find "$TEST_DATA_DIR" -name "*.kt" -o -name "*.sql" -o -name "*.yml" | wc -l)
echo "📄 Total files for analysis: $TOTAL_FILES"
echo ""

# Detailed analysis by file type
echo -e "${BLUE}📋 FILE INVENTORY:${NC}"
echo "------------------"

echo "🔸 Kotlin Repositories:"
find "$TEST_DATA_DIR" -name "*Repository.kt" -exec basename {} \; | sed 's/^/  - /'

echo "🔸 Services:"
find "$TEST_DATA_DIR" -name "*Service.kt" -exec basename {} \; | sed 's/^/  - /'

echo "🔸 Entities:"
find "$TEST_DATA_DIR" -name "*Entity.kt" -o -name "User.kt" -exec basename {} \; | sed 's/^/  - /'

echo "🔸 SQL Migrations:"
find "$TEST_DATA_DIR" -name "*.sql" -exec basename {} \; | sed 's/^/  - /'

echo "🔸 Configuration Files:"
find "$TEST_DATA_DIR" -name "*.yml" -exec basename {} \; | sed 's/^/  - /'

echo ""

# Problematic pattern analysis
echo -e "${RED}🔍 PROBLEMATIC PATTERN DETECTION:${NC}"
echo "================================="

# Counters
CRITICAL_ISSUES=0
WARNING_ISSUES=0
TOTAL_QUERIES=0

# Function to count and report issues with fix suggestions
analyze_pattern() {
    local pattern="$1"
    local description="$2"
    local severity="$3"
    local explanation="$4"
    local suggestion="$5"
    local files_found=$(grep -r "$pattern" "$TEST_DATA_DIR" --include="*.kt" --include="*.sql" | wc -l)
    
    if [ $files_found -gt 0 ]; then
        case $severity in
            "CRITICAL")
                echo -e "🔴 ${description}: $files_found occurrences"
                CRITICAL_ISSUES=$((CRITICAL_ISSUES + files_found))
                ;;
            "WARNING")
                echo -e "🟡 ${description}: $files_found occurrences"
                WARNING_ISSUES=$((WARNING_ISSUES + files_found))
                ;;
        esac
        
        echo "   📋 Problem: $explanation"
        echo "   💡 Solution: $suggestion"
        echo ""
        echo "   📄 Code examples found:"
        grep -r "$pattern" "$TEST_DATA_DIR" --include="*.kt" --include="*.sql" -n | head -3 | while read line; do
            echo "   - $line"
        done
        echo ""
    fi
}

echo "Scanning for critical patterns..."
echo ""

# Critical patterns with explanations and suggestions
analyze_pattern "SELECT \*" "Full Table Scan (SELECT *)" "CRITICAL" \
    "SELECT * forces the database to return all columns, unnecessarily increasing network traffic and memory consumption" \
    "Specify only necessary columns: SELECT id, name, email FROM users"

analyze_pattern "LIKE '%.*'" "Leading Wildcard LIKE" "CRITICAL" \
    "LIKE with leading wildcard ('%pattern') prevents index usage, forcing full table scan" \
    "Use full-text search or restructure query: WHERE email LIKE 'user@%' (wildcard at end)"

analyze_pattern "FROM.*,.*WHERE" "Cartesian Product" "CRITICAL" \
    "Comma between tables without explicit JOIN creates cartesian product, returning millions of unnecessary rows" \
    "Use explicit JOIN: SELECT u.*, o.* FROM users u INNER JOIN orders o ON u.id = o.user_id"

analyze_pattern "fetch = FetchType.EAGER" "EAGER Loading (N+1)" "CRITICAL" \
    "EAGER loading loads relationships immediately, causing N+1 queries and performance degradation" \
    "Use LAZY loading (default) and @BatchSize or fetch joins when needed: fetch = FetchType.LAZY"

analyze_pattern "ddl-auto: update" "DDL Auto Update in Production" "CRITICAL" \
    "Schema auto-update in production can cause data loss and unplanned downtime" \
    "Use controlled migrations: ddl-auto: validate or ddl-auto: none with Flyway/Liquibase"

# Warning patterns with explanations
analyze_pattern "UPPER(" "Non-SARGable WHERE" "WARNING" \
    "Functions on WHERE columns prevent index usage, making queries slow" \
    "Create functional index or use case-insensitive collation: WHERE name ILIKE 'john'"

analyze_pattern "show-sql: true" "SQL Logging in Production" "WARNING" \
    "SQL logging in production can expose sensitive data and degrade performance" \
    "Disable in production: show-sql: false or use specific profiles"

analyze_pattern "createNativeQuery" "Unvalidated Native Queries" "WARNING" \
    "Native queries bypass ORM validations and may have SQL injection vulnerabilities" \
    "Use JPQL/HQL when possible or validate parameters: createQuery() with named parameters"

# Count total queries
TOTAL_QUERIES=$(grep -r "@Query\|createQuery\|createNativeQuery" "$TEST_DATA_DIR" --include="*.kt" | wc -l)

echo ""
echo -e "${BLUE}📊 ANALYSIS SUMMARY:${NC}"
echo "==================="
echo "📄 Files analyzed: $TOTAL_FILES"
echo "🔍 Queries found: $TOTAL_QUERIES"
echo -e "🔴 Critical issues: ${RED}$CRITICAL_ISSUES${NC}"
echo -e "🟡 Warnings: ${YELLOW}$WARNING_ISSUES${NC}"
echo ""

# Determine semaphore status
if [ $CRITICAL_ISSUES -gt 0 ]; then
    DEPLOY_STATUS="BLOCKED"
    STATUS_COLOR=$RED
    STATUS_EMOJI="🔴"
elif [ $WARNING_ISSUES -gt 0 ]; then
    DEPLOY_STATUS="WARNING"
    STATUS_COLOR=$YELLOW
    STATUS_EMOJI="🟡"
else
    DEPLOY_STATUS="APPROVED"
    STATUS_COLOR=$GREEN
    STATUS_EMOJI="🟢"
fi

echo -e "${STATUS_COLOR}${STATUS_EMOJI} DEPLOY STATUS: $DEPLOY_STATUS${NC}"
echo ""

# Generate Markdown report
REPORT_FILE="$REPORTS_DIR/database-guardian-report-$(date +%Y%m%d-%H%M%S).md"

cat > "$REPORT_FILE" << EOF
# 🛡️ Database Guardian Analysis Report

$(echo -e "![Status](https://img.shields.io/badge/Deploy-$DEPLOY_STATUS-$(echo $DEPLOY_STATUS | tr '[:upper:]' '[:lower:]')) $STATUS_EMOJI")

## 📊 Summary
- **Files Scanned:** $TOTAL_FILES
- **Queries Analyzed:** $TOTAL_QUERIES
- **Critical Issues:** $CRITICAL_ISSUES
- **Warnings:** $WARNING_ISSUES
- **Status:** $DEPLOY_STATUS

## 🔍 Analysis Details

### Files Analyzed
$(find "$TEST_DATA_DIR" -name "*.kt" -o -name "*.sql" -o -name "*.yml" | sed 's|'$TEST_DATA_DIR'/||' | sed 's/^/- /')

### Critical Issues Found 🔴

EOF

# Add critical issue details with suggestions
if [ $CRITICAL_ISSUES -gt 0 ]; then
    echo "#### 🔴 SELECT * Anti-pattern" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Problem:** SELECT * forces the database to return all columns, unnecessarily increasing network traffic and memory consumption" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**💡 Solution:** Specify only necessary columns: \`SELECT id, name, email FROM users\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Occurrences found:**" >> "$REPORT_FILE"
    grep -r "SELECT \*" "$TEST_DATA_DIR" --include="*.kt" --include="*.sql" -n | head -5 | while read line; do
        file_line=$(echo "$line" | cut -d: -f1-2)
        code_fragment=$(echo "$line" | cut -d: -f3- | sed 's/^[[:space:]]*//')
        echo "- **File:** \`$file_line\`" >> "$REPORT_FILE"
        echo "  \`\`\`sql" >> "$REPORT_FILE"
        echo "  $code_fragment" >> "$REPORT_FILE"
        echo "  \`\`\`" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    done
    echo "" >> "$REPORT_FILE"
    
    echo "#### 🔴 Leading Wildcard LIKE" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Problem:** LIKE with leading wildcard ('%pattern') prevents index usage, forcing full table scan" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**💡 Solution:** Use full-text search or restructure query: \`WHERE email LIKE 'user@%'\` (wildcard at end)" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Occurrences found:**" >> "$REPORT_FILE"
    grep -r "LIKE '%.*'" "$TEST_DATA_DIR" --include="*.kt" --include="*.sql" -n | while read line; do
        file_line=$(echo "$line" | cut -d: -f1-2)
        code_fragment=$(echo "$line" | cut -d: -f3- | sed 's/^[[:space:]]*//')
        echo "- **File:** \`$file_line\`" >> "$REPORT_FILE"
        echo "  \`\`\`sql" >> "$REPORT_FILE"
        echo "  $code_fragment" >> "$REPORT_FILE"
        echo "  \`\`\`" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    done
    echo "" >> "$REPORT_FILE"
    
    echo "#### 🔴 EAGER Loading Issues" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Problem:** EAGER loading loads relationships immediately, causing N+1 queries and performance degradation" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**💡 Solution:** Use LAZY loading (default) and @BatchSize or fetch joins when needed: \`fetch = FetchType.LAZY\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Occurrences found:**" >> "$REPORT_FILE"
    grep -r "fetch = FetchType.EAGER" "$TEST_DATA_DIR" --include="*.kt" -n | while read line; do
        file_line=$(echo "$line" | cut -d: -f1-2)
        code_fragment=$(echo "$line" | cut -d: -f3- | sed 's/^[[:space:]]*//')
        echo "- **File:** \`$file_line\`" >> "$REPORT_FILE"
        echo "  \`\`\`kotlin" >> "$REPORT_FILE"
        echo "  $code_fragment" >> "$REPORT_FILE"
        echo "  \`\`\`" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    done
    echo "" >> "$REPORT_FILE"
    
    echo "#### 🔴 Cartesian Product" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Problem:** Comma between tables without explicit JOIN creates cartesian product, returning millions of unnecessary rows" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**💡 Solution:** Use explicit JOIN: \`SELECT u.*, o.* FROM users u INNER JOIN orders o ON u.id = o.user_id\`" >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
    echo "**Occurrences found:**" >> "$REPORT_FILE"
    grep -r "FROM.*,.*WHERE" "$TEST_DATA_DIR" --include="*.kt" --include="*.sql" -n | while read line; do
        file_line=$(echo "$line" | cut -d: -f1-2)
        code_fragment=$(echo "$line" | cut -d: -f3- | sed 's/^[[:space:]]*//')
        echo "- **File:** \`$file_line\`" >> "$REPORT_FILE"
        echo "  \`\`\`sql" >> "$REPORT_FILE"
        echo "  $code_fragment" >> "$REPORT_FILE"
        echo "  \`\`\`" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    done
    echo "" >> "$REPORT_FILE"
fi

cat >> "$REPORT_FILE" << EOF

## 🎯 Recommendations

1. **Replace SELECT \*** with specific column lists
2. **Avoid leading wildcards** in LIKE clauses - use full-text search instead
3. **Use LAZY loading** instead of EAGER to prevent N+1 queries
4. **Add proper indexes** on frequently queried columns
5. **Use parameterized queries** to prevent SQL injection

---
*Generated by Database Guardian - $(date)*
*Preventing database disasters since 2024* 🛡️
EOF

echo -e "${GREEN}✅ Report generated: $REPORT_FILE${NC}"
echo ""

# Generate JSON report for CI/CD
JSON_REPORT="$REPORTS_DIR/database-guardian-report-$(date +%Y%m%d-%H%M%S).json"

cat > "$JSON_REPORT" << EOF
{
  "status": "$DEPLOY_STATUS",
  "shouldBlockDeploy": $([ "$DEPLOY_STATUS" = "BLOCKED" ] && echo "true" || echo "false"),
  "timestamp": "$(date -Iseconds)",
  "summary": {
    "filesScanned": $TOTAL_FILES,
    "queriesAnalyzed": $TOTAL_QUERIES,
    "criticalIssues": $CRITICAL_ISSUES,
    "warnings": $WARNING_ISSUES,
    "approved": $([ $CRITICAL_ISSUES -eq 0 ] && [ $WARNING_ISSUES -eq 0 ] && echo "1" || echo "0")
  },
  "criticalIssueCount": $CRITICAL_ISSUES,
  "warningCount": $WARNING_ISSUES
}
EOF

echo -e "${GREEN}✅ JSON report generated: $JSON_REPORT${NC}"
echo ""

# Display report contents
echo -e "${BLUE}📋 MARKDOWN REPORT:${NC}"
echo "==================="
cat "$REPORT_FILE"
echo ""

echo -e "${BLUE}📋 JSON REPORT:${NC}"
echo "==============="
cat "$JSON_REPORT"
echo ""

# Final status
echo "🏁 SMOKE TEST COMPLETED!"
echo "========================"
echo ""

if [ "$DEPLOY_STATUS" = "BLOCKED" ]; then
    echo -e "${RED}❌ DEPLOY BLOCKED - Critical issues found!${NC}"
    echo "   🔧 Fix $CRITICAL_ISSUES critical issues before deployment"
    exit 1
elif [ "$DEPLOY_STATUS" = "WARNING" ]; then
    echo -e "${YELLOW}⚠️ DEPLOY WITH WARNINGS - Review recommended${NC}"
    echo "   📝 $WARNING_ISSUES warnings found"
    exit 0
else
    echo -e "${GREEN}✅ DEPLOY APPROVED - No critical issues found!${NC}"
    echo "   🚀 Ready for production!"
    exit 0
fi