import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Custom metrics for load testing
const analysisFailureRate = new Rate('analysis_failures');
const analysisResponseTime = new Trend('analysis_response_time', true);
const issueDetectionRate = new Trend('issues_detected');

const BASE_URL = 'http://localhost:8080';
const API_TOKEN = 'abacaxi';

// Load test config
export const options = {
  stages: [
    { duration: '10s', target: 1 },   // Warm up
    { duration: '20s', target: 2 },   // Normal load
    { duration: '10s', target: 3 },   // Peak load  
    { duration: '10s', target: 0 },   // Cool down
  ],
  thresholds: {
    http_req_duration: ['p(95)<30000'], // 95% of requests under 30s
    analysis_failures: ['rate<0.3'],    // Less than 30% failures
    analysis_response_time: ['p(90)<25000'], // 90% complete under 25s
  },
};

// Test using test data with known SQL issues
const analysisPayload = {
  mode: "STATIC",
  source: "src/test/resources/test-data-2", // Test data with SQL issues
  config: {
    dialect: "POSTGRESQL"
  }
};

export default function () {
  // Health check - infrastructure monitoring
  const healthResponse = http.get(`${BASE_URL}/actuator/health`, {
    headers: { 'x-api-token': API_TOKEN },
  });
  
  const healthOk = check(healthResponse, {
    'health check status is 200': (r) => r.status === 200,
  });

  if (!healthOk) {
    analysisFailureRate.add(1);
    return;
  }

  // Start analysis - business logic testing
  const startTime = Date.now();
  
  const analysisResponse = http.post(
    `${BASE_URL}/api/v1/analysis`,
    JSON.stringify(analysisPayload),
    {
      headers: {
        'Content-Type': 'application/json',
        'x-api-token': API_TOKEN,
      },
    }
  );

  const analysisStartSuccess = check(analysisResponse, {
    'analysis started successfully': (r) => r.status === 200,
    'run ID returned': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.runId && body.runId.length > 0;
      } catch (e) {
        console.log(`Failed to parse response: ${r.body}`);
        return false;
      }
    },
  });

  if (!analysisStartSuccess) {
    analysisFailureRate.add(1);
    console.log(`Analysis start failed: ${analysisResponse.status} - ${analysisResponse.body}`);
    return;
  }

  const runId = JSON.parse(analysisResponse.body).runId;
  console.log(`🚀 Started analysis: ${runId}`);

  // Poll for completion - endurance testing
  let status = 'running';
  let attempts = 0;
  const maxAttempts = 60; // 2 minutes max (realistic timeout)

  while (status === 'running' && attempts < maxAttempts) {
    sleep(2);
    attempts++;

    const statusResponse = http.get(`${BASE_URL}/api/v1/report/${runId}`, {
      headers: { 'x-api-token': API_TOKEN },
    });

    if (statusResponse.status === 200) {
      try {
        const statusBody = JSON.parse(statusResponse.body);
        status = statusBody.status;
        
        if (status === 'completed') {
          const endTime = Date.now();
          const totalTime = endTime - startTime;
          
          analysisResponseTime.add(totalTime);
          
          // Parse issue count from summary
          if (statusBody.summary) {
            const issueMatch = statusBody.summary.match(/(\d+)\s+issues?/);
            if (issueMatch) {
              const issueCount = parseInt(issueMatch[1]);
              issueDetectionRate.add(issueCount);
            }
          }
          
          console.log(`✅ Analysis ${runId} completed in ${totalTime}ms - Found: ${statusBody.critical || 0} critical, ${statusBody.warnings || 0} warnings`);
          break;
          
        } else if (status === 'failed') {
          analysisFailureRate.add(1);
          console.log(`❌ Analysis ${runId} failed: ${statusBody.summary}`);
          break;
        }
        
        // Progress indicator
        if (attempts % 10 === 0) {
          console.log(`⏳ Analysis ${runId} still running... (${attempts * 2}s elapsed)`);
        }
        
      } catch (e) {
        console.log(`Error parsing status response for ${runId}: ${e}`);
      }
    } else {
      console.log(`Status check failed for ${runId}: ${statusResponse.status}`);
    }
  }

  if (attempts >= maxAttempts) {
    analysisFailureRate.add(1);
    console.log(`⏰ Analysis ${runId} timed out after ${attempts * 2}s`);
  }

  // Random delay to simulate realistic user behavior
  sleep(1 + Math.random() * 2);
}