#!/bin/bash

# DB Guardian K6 Load Testing Runner
# Runs comprehensive load tests and generates reports

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$ROOT_DIR/load-test-results"
BASE_URL="http://localhost:8080"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
NC='\033[0m'

print_header() {
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}    DB Guardian K6 Load Testing Suite     ${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo
}

print_test() {
    echo -e "${PURPLE}🚀 $1${NC}"
}

print_success() {
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

# Check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check if k6 is installed
    if ! command -v k6 >/dev/null 2>&1; then
        print_error "k6 is not installed. Install it from: https://k6.io/docs/getting-started/installation/"
        exit 1
    fi
    
    # Check if application is running
    if ! curl -f "$BASE_URL/actuator/health" >/dev/null 2>&1; then
        print_error "DB Guardian is not running. Start it with:"
        echo "   S3_ENDPOINT=http://localhost:4566 ./gradlew bootRun"
        exit 1
    fi
    
    # Check if docker services are running
    if ! curl -f http://localhost:4566/_localstack/health >/dev/null 2>&1; then
        print_warning "LocalStack might not be running. Start with: docker-compose up -d"
    fi
    
    print_success "Prerequisites check passed"
}

# Setup results directory
setup_results() {
    mkdir -p "$RESULTS_DIR"
    rm -f "$RESULTS_DIR"/*.json "$RESULTS_DIR"/*.html
    
    print_info "Results will be saved to: $RESULTS_DIR"
}

# Run individual test
run_test() {
    local test_name="$1"
    local test_file="$2"
    local description="$3"
    
    print_test "Running $test_name test: $description"
    
    local output_file="$RESULTS_DIR/${test_name}_results.json"
    local summary_file="$RESULTS_DIR/${test_name}_summary.json"
    
    # Run k6 test with JSON output
    k6 run \
        --out json="$output_file" \
        --summary-export="$summary_file" \
        "$SCRIPT_DIR/$test_file"
    
    local exit_code=$?
    
    if [ $exit_code -eq 0 ]; then
        print_success "$test_name test completed successfully"
    else
        print_warning "$test_name test completed with issues (exit code: $exit_code)"
    fi
    
    # Extract key metrics
    if [ -f "$summary_file" ]; then
        echo "Key metrics for $test_name:"
        jq -r '
        "  📊 Total requests: " + (.root_group.checks.passes + .root_group.checks.fails | tostring) +
        "\n  ✅ Passed checks: " + (.root_group.checks.passes | tostring) +
        "\n  ❌ Failed checks: " + (.root_group.checks.fails | tostring) +
        "\n  ⏱️  Avg response time: " + (.metrics.http_req_duration.avg | tostring) + "ms" +
        "\n  📈 95th percentile: " + (.metrics.http_req_duration.p95 | tostring) + "ms"
        ' "$summary_file" 2>/dev/null || echo "  Unable to parse metrics"
    fi
    
    echo
    return $exit_code
}

# Generate comprehensive report
generate_report() {
    print_info "Generating comprehensive test report..."
    
    local report_file="$RESULTS_DIR/load_test_report.html"
    
    cat > "$report_file" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>DB Guardian Load Test Report</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 40px; }
        .header { background: #1f2937; color: white; padding: 20px; border-radius: 8px; margin-bottom: 30px; }
        .test-section { background: #f9fafb; padding: 20px; margin: 20px 0; border-radius: 8px; border-left: 4px solid #3b82f6; }
        .metric { display: inline-block; margin: 10px 20px 10px 0; padding: 10px; background: white; border-radius: 4px; border: 1px solid #e5e7eb; }
        .success { border-left-color: #10b981; }
        .warning { border-left-color: #f59e0b; }
        .error { border-left-color: #ef4444; }
        .chart { margin: 20px 0; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #e5e7eb; }
        th { background: #f3f4f6; font-weight: 600; }
        .status-ok { color: #10b981; }
        .status-warn { color: #f59e0b; }
        .status-error { color: #ef4444; }
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 DB Guardian Load Test Report</h1>
        <p>Generated on: <span id="timestamp"></span></p>
    </div>
EOF

    # Add timestamp
    echo "    <script>document.getElementById('timestamp').textContent = new Date().toLocaleString();</script>" >> "$report_file"

    # Process each test result
    for test_type in load; do
        local summary_file="$RESULTS_DIR/${test_type}_summary.json"
        
        if [ -f "$summary_file" ]; then
            echo "    <div class=\"test-section\">" >> "$report_file"
            echo "        <h2>Professional Load Test Results</h2>" >> "$report_file"
            
            # Extract and format metrics
            jq -r '
            def format_duration: if . then (. / 1000 | tostring) + "s" else "N/A" end;
            def format_rate: if . then (. * 100 | floor | tostring) + "%" else "N/A" end;
            
            "        <div class=\"metric\">" +
            "            <strong>Total Requests:</strong> " + (.root_group.checks.passes + .root_group.checks.fails | tostring) +
            "        </div>" +
            "        <div class=\"metric\">" +
            "            <strong>Success Rate:</strong> " + ((.root_group.checks.passes / (.root_group.checks.passes + .root_group.checks.fails)) | format_rate) +
            "        </div>" +
            "        <div class=\"metric\">" +
            "            <strong>Avg Response:</strong> " + (.metrics.http_req_duration.avg / 1000 | floor | tostring) + "ms" +
            "        </div>" +
            "        <div class=\"metric\">" +
            "            <strong>95th Percentile:</strong> " + (.metrics.http_req_duration.p95 / 1000 | floor | tostring) + "ms" +
            "        </div>"
            ' "$summary_file" 2>/dev/null >> "$report_file" || echo "        <p>Unable to parse metrics for $test_type</p>" >> "$report_file"
            
            echo "    </div>" >> "$report_file"
        fi
    done

    # Add analysis and recommendations
    cat >> "$report_file" << 'EOF'
    
    <div class="test-section">
        <h2>📊 Performance Analysis</h2>
        <table>
            <thead>
                <tr>
                    <th>Test Type</th>
                    <th>Load Pattern</th>
                    <th>Expected Use Case</th>
                    <th>Performance Target</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>Simple Analysis</td>
                    <td>2-10 concurrent users</td>
                    <td>Small team CI/CD</td>
                    <td>&lt; 30s response time</td>
                </tr>
                <tr>
                    <td>Complex Analysis</td>
                    <td>3-15 concurrent users</td>
                    <td>Large codebase analysis</td>
                    <td>&lt; 60s response time</td>
                </tr>
                <tr>
                    <td>Burst Testing</td>
                    <td>20-30 concurrent spikes</td>
                    <td>CI/CD rush periods</td>
                    <td>&lt; 45s during bursts</td>
                </tr>
            </tbody>
        </table>
    </div>

    <div class="test-section">
        <h2>🎯 Recommendations</h2>
        <h3>Performance Optimization</h3>
        <ul>
            <li><strong>Response Time:</strong> Consider implementing analysis result caching for repeated requests</li>
            <li><strong>Concurrency:</strong> Monitor memory usage during high concurrent loads</li>
            <li><strong>Scalability:</strong> Implement async processing for better throughput</li>
        </ul>
        
        <h3>Production Deployment</h3>
        <ul>
            <li><strong>Resource Planning:</strong> Allocate sufficient memory for concurrent analysis</li>
            <li><strong>Monitoring:</strong> Set up alerts for response time degradation</li>
            <li><strong>Auto-scaling:</strong> Consider horizontal scaling for burst loads</li>
        </ul>
        
        <h3>CI/CD Integration</h3>
        <ul>
            <li><strong>Timeout Configuration:</strong> Set appropriate timeouts based on codebase size</li>
            <li><strong>Rate Limiting:</strong> Implement intelligent queueing for burst scenarios</li>
            <li><strong>Retry Logic:</strong> Add exponential backoff for transient failures</li>
        </ul>
    </div>

</body>
</html>
EOF

    print_success "HTML report generated: $report_file"
    
    # Try to open in browser (macOS/Linux)
    if command -v open >/dev/null 2>&1; then
        open "$report_file"
    elif command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$report_file"
    else
        print_info "Open this file in your browser: $report_file"
    fi
}

# Main execution
main() {
    print_header
    
    check_prerequisites
    setup_results
    
    local overall_status=0
    
    print_info "Starting comprehensive load testing suite..."
    echo
    
    # Professional Load Test
    if ! run_test "load" "load-test.js" "Professional end-to-end load test with working analysis"; then
        overall_status=1
    fi
    
    # Generate comprehensive report
    generate_report
    
    # Final summary
    echo
    print_info "Load testing completed!"
    print_info "Results saved in: $RESULTS_DIR"
    
    if [ $overall_status -eq 0 ]; then
        print_success "All tests passed! DB Guardian is ready for production."
    else
        print_warning "Some tests had issues. Review the results and consider optimizations."
    fi
    
    return $overall_status
}

# Show usage
show_usage() {
    echo "DB Guardian K6 Load Testing Suite"
    echo "================================="
    echo
    echo "Usage: $0 [test-type]"
    echo
    echo "Test types:"
    echo "  load        Run professional load test"
    echo "  all         Run all tests (default)"
    echo "  --help      Show this help"
    echo
    echo "Prerequisites:"
    echo "  - k6 installed (https://k6.io/docs/getting-started/installation/)"
    echo "  - DB Guardian running on localhost:8080"
    echo "  - Docker services (postgres, localstack) running"
    echo "  - jq installed for report generation"
    echo
}

# Handle arguments
case "${1:-all}" in
    "load")
        print_header
        check_prerequisites
        setup_results
        run_test "load" "load-test.js" "Professional load test"
        ;;
    "all")
        main
        ;;
    "--help"|"-h")
        show_usage
        ;;
    *)
        print_error "Unknown test type: $1"
        show_usage
        exit 1
        ;;
esac