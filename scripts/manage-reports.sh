#!/bin/bash

# DB Guardian Report Management Script
# Provides easy access to analysis reports stored in S3

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
REPORTS_DIR="$ROOT_DIR/reports"
S3_ENDPOINT="http://localhost:4566"
S3_BUCKET="db-guardian-reports"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️ $1${NC}"
}

# Check if LocalStack is running
check_localstack() {
    if ! curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; then
        print_error "LocalStack is not running. Start it with: docker-compose up -d"
        exit 1
    fi
}

# Create reports directory if it doesn't exist
ensure_reports_dir() {
    mkdir -p "$REPORTS_DIR"
}

# List all reports
list_reports() {
    print_info "📋 Listing all reports in S3..."
    check_localstack
    
    echo
    echo "Available reports:"
    echo "=================="
    
    # List reports with details
    aws --endpoint-url="$S3_ENDPOINT" s3 ls s3://$S3_BUCKET/reports/ --recursive --human-readable | \
    while read -r line; do
        if [[ $line == *".json" ]]; then
            # Extract date, size, and filename
            date_time=$(echo "$line" | awk '{print $1, $2}')
            size=$(echo "$line" | awk '{print $3}')
            filepath=$(echo "$line" | awk '{print $4}')
            filename=$(basename "$filepath" .json)
            
            echo "📄 Report ID: $filename"
            echo "   📅 Date: $date_time"
            echo "   💾 Size: $size"
            echo "   📁 Path: $filepath"
            echo
        fi
    done
    
    # Count total reports
    total=$(aws --endpoint-url="$S3_ENDPOINT" s3 ls s3://$S3_BUCKET/reports/ --recursive | grep -c "\.json" || echo "0")
    print_status "Found $total reports total"
}

# Download a specific report
download_report() {
    local report_id="$1"
    
    if [[ -z "$report_id" ]]; then
        print_error "Report ID is required"
        echo "Usage: $0 download <report-id>"
        exit 1
    fi
    
    print_info "📥 Downloading report: $report_id"
    check_localstack
    ensure_reports_dir
    
    # Find the report file in S3
    local s3_path
    s3_path=$(aws --endpoint-url="$S3_ENDPOINT" s3 ls s3://$S3_BUCKET/reports/ --recursive | grep "$report_id.json" | awk '{print $4}' | head -1)
    
    if [[ -z "$s3_path" ]]; then
        print_error "Report with ID '$report_id' not found"
        exit 1
    fi
    
    local local_file="$REPORTS_DIR/$report_id.json"
    
    # Download the report
    aws --endpoint-url="$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$s3_path" "$local_file"
    
    print_status "Report downloaded to: $local_file"
    
    # Show basic info about the report
    if command -v jq >/dev/null 2>&1; then
        echo
        print_info "📊 Report Summary:"
        echo "=================="
        jq -r '
        "🆔 Run ID: " + .runId +
        "\n📅 Started: " + .startedAt +
        "\n✅ Completed: " + (.completedAt // "N/A") +
        "\n📊 Status: " + .status +
        "\n🔍 Issues Found: " + (.summary.totalIssues | tostring) +
        "\n🚨 Critical: " + (.summary.criticalIssues | tostring) +
        "\n⚠️  Warnings: " + (.summary.warningIssues | tostring) +
        "\nℹ️  Info: " + (.summary.infoIssues | tostring) +
        "\n📁 Files Analyzed: " + (.summary.filesAnalyzed | tostring) +
        "\n🔎 Queries Analyzed: " + (.summary.queriesAnalyzed | tostring)
        ' "$local_file"
    fi
}

# Download all reports
download_all() {
    print_info "📥 Downloading all reports..."
    check_localstack
    ensure_reports_dir
    
    # Get list of all report files
    local report_files
    report_files=$(aws --endpoint-url="$S3_ENDPOINT" s3 ls s3://$S3_BUCKET/reports/ --recursive | grep "\.json" | awk '{print $4}')
    
    if [[ -z "$report_files" ]]; then
        print_warning "No reports found in S3"
        return
    fi
    
    local count=0
    echo "$report_files" | while read -r s3_path; do
        if [[ -n "$s3_path" ]]; then
            local filename=$(basename "$s3_path")
            local local_file="$REPORTS_DIR/$filename"
            
            aws --endpoint-url="$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$s3_path" "$local_file" >/dev/null
            echo "📄 Downloaded: $filename"
            ((count++))
        fi
    done
    
    print_status "Downloaded all reports to: $REPORTS_DIR"
}

# View a report with formatted output  
view_report() {
    local report_id="$1"
    
    if [[ -z "$report_id" ]]; then
        print_error "Report ID is required"
        echo "Usage: $0 view <report-id>"
        exit 1
    fi
    
    local local_file="$REPORTS_DIR/$report_id.json"
    
    # Check if report exists locally, if not download it
    if [[ ! -f "$local_file" ]]; then
        print_info "Report not found locally, downloading..."
        download_report "$report_id"
    fi
    
    if [[ ! -f "$local_file" ]]; then
        print_error "Could not find or download report: $report_id"
        exit 1
    fi
    
    print_info "📖 Viewing report: $report_id"
    echo
    
    if command -v jq >/dev/null 2>&1; then
        # Detailed formatted view
        echo "======================================"
        echo "           ANALYSIS REPORT"
        echo "======================================"
        
        # Header info
        jq -r '
        "🆔 Run ID: " + .runId +
        "\n📅 Started: " + .startedAt +
        "\n✅ Completed: " + (.completedAt // "N/A") +
        "\n📊 Status: " + .status +
        "\n🎯 Mode: " + .mode
        ' "$local_file"
        
        echo
        echo "======================================"
        echo "            SUMMARY"
        echo "======================================"
        
        jq -r '
        "🔍 Total Issues: " + (.summary.totalIssues | tostring) +
        "\n🚨 Critical: " + (.summary.criticalIssues | tostring) +
        "\n⚠️  Warnings: " + (.summary.warningIssues | tostring) +
        "\nℹ️  Info: " + (.summary.infoIssues | tostring) +
        "\n📁 Files Analyzed: " + (.summary.filesAnalyzed | tostring) +
        "\n🔎 Queries Analyzed: " + (.summary.queriesAnalyzed | tostring)
        ' "$local_file"
        
        echo
        echo "======================================"
        echo "            ISSUES FOUND"
        echo "======================================"
        
        # Show issues grouped by severity
        local critical_count=$(jq '.issues | map(select(.severity == "CRITICAL")) | length' "$local_file")
        if [[ "$critical_count" -gt 0 ]]; then
            echo
            echo "🚨 CRITICAL ISSUES ($critical_count):"
            echo "-----------------------------------"
            jq -r '.issues[] | select(.severity == "CRITICAL") | 
            "📍 " + .technique + " (Confidence: " + (.confidence * 100 | floor | tostring) + "%)" +
            "\n   🔍 " + .description +
            "\n   💡 " + .suggestion +
            "\n   📁 " + .location.filePath +
            "\n   📝 Query: " + (.queryText | .[0:100]) + (if (.queryText | length) > 100 then "..." else "" end) +
            "\n"' "$local_file"
        fi
        
        local warning_count=$(jq '.issues | map(select(.severity == "WARNING")) | length' "$local_file")
        if [[ "$warning_count" -gt 0 ]]; then
            echo
            echo "⚠️  WARNING ISSUES ($warning_count):"
            echo "----------------------------------"
            jq -r '.issues[] | select(.severity == "WARNING") | 
            "📍 " + .technique + " (Confidence: " + (.confidence * 100 | floor | tostring) + "%)" +
            "\n   🔍 " + .description +
            "\n   💡 " + .suggestion +
            "\n   📁 " + .location.filePath +
            "\n   📝 Query: " + (.queryText | .[0:100]) + (if (.queryText | length) > 100 then "..." else "" end) +
            "\n"' "$local_file"
        fi
        
        local info_count=$(jq '.issues | map(select(.severity == "INFO")) | length' "$local_file")
        if [[ "$info_count" -gt 0 ]]; then
            echo
            echo "ℹ️  INFO ISSUES ($info_count):"
            echo "----------------------------"
            jq -r '.issues[] | select(.severity == "INFO") | 
            "📍 " + .technique + " (Confidence: " + (.confidence * 100 | floor | tostring) + "%)" +
            "\n   🔍 " + .description +
            "\n   💡 " + .suggestion +
            "\n   📁 " + .location.filePath +
            "\n   📝 Query: " + (.queryText | .[0:100]) + (if (.queryText | length) > 100 then "..." else "" end) +
            "\n"' "$local_file"
        fi
        
    else
        print_warning "jq is not installed. Showing raw JSON:"
        cat "$local_file"
    fi
}

# Show usage
show_usage() {
    echo "DB Guardian Report Management"
    echo "============================"
    echo
    echo "Usage: $0 <command> [options]"
    echo
    echo "Commands:"
    echo "  list                    List all available reports"
    echo "  download <report-id>    Download a specific report"
    echo "  download-all           Download all reports"
    echo "  view <report-id>       View a report with formatted output"
    echo "  help                   Show this help message"
    echo
    echo "Examples:"
    echo "  $0 list"
    echo "  $0 download f2a0bc14-dca3-48be-9194-a5402b13b5f7"
    echo "  $0 view f2a0bc14-dca3-48be-9194-a5402b13b5f7"
    echo "  $0 download-all"
    echo
    echo "Notes:"
    echo "- Reports are downloaded to: $REPORTS_DIR"
    echo "- LocalStack must be running on port 4566"
    echo "- Install 'jq' for better formatted output"
}

# Main script logic
case "${1:-}" in
    "list")
        list_reports
        ;;
    "download")
        download_report "$2"
        ;;
    "download-all")
        download_all
        ;;
    "view")
        view_report "$2"
        ;;
    "help"|"--help"|"-h")
        show_usage
        ;;
    "")
        show_usage
        ;;
    *)
        print_error "Unknown command: $1"
        echo
        show_usage
        exit 1
        ;;
esac