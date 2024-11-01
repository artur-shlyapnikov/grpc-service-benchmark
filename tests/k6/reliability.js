import { sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { Client } from "k6/net/grpc";
import { calculateMetrics } from "./helpers.js";

const TEST_CONFIG = {
  TARGET_LOAD: 20302,
  TEST_DURATION: 30 * 60, // 30 minutes
  RAMP_UP_DURATION: 5 * 60, // 5 minutes
  MEASUREMENT_WINDOW: 5 * 60, // 5 minutes
  MAX_ERROR_RATE: 0.01,
  MAX_THROUGHPUT_VARIANCE: 0.1,
  MAX_P99_LATENCY: 500.0,
  REQUIRED_STABLE_WINDOWS: 6,
};

const errorRate = new Rate("errors");
const callDuration = new Trend("call_duration");
const throughput = new Trend("throughput");

export const options = {
  stages: [
    { duration: "5m", target: TEST_CONFIG.TARGET_LOAD },
    { duration: "30m", target: TEST_CONFIG.TARGET_LOAD },
  ],
  thresholds: {
    "call_duration{kind:p99}": ["p(99)<500"],
    errors: ["rate<0.01"],
  },
};

const client = new Client();
client.load(["definitions"], "helloworld.proto");

export default function () {
  const resp = client.invoke("helloworld.Greeter/SayHello", {
    name: "World",
  });

  check(resp, {
    "status is OK": (r) => r && r.status === grpc.StatusOK,
  });

  callDuration.add(resp.duration);
  errorRate.add(resp.status !== grpc.StatusOK);
  throughput.add(1);

  sleep(1.0 / TEST_CONFIG.TARGET_LOAD);
}

export function handleSummary(data) {
  const metrics = [];
  let elapsed = 0;

  while (elapsed < TEST_CONFIG.TEST_DURATION) {
    metrics.push(
      calculateMetrics(
        {
          errors: errorRate.value,
          latency: callDuration,
          iterations: __VU * __ITER,
        },
        data.startTime + elapsed * 1000
      )
    );

    elapsed += TEST_CONFIG.MEASUREMENT_WINDOW;
  }

  analyzeResults(metrics);
}

function analyzeResults(metrics) {
  const avgThroughput = metrics.reduce((sum, m) => sum + m.currentThroughput, 0) / metrics.length;
  const maxThroughput = Math.max(...metrics.map((m) => m.currentThroughput));
  const minThroughput = Math.min(...metrics.map((m) => m.currentThroughput));
  const avgErrorRate = metrics.reduce((sum, m) => sum + m.errorRate, 0) / metrics.length;
  const avgP99Latency = metrics.reduce((sum, m) => sum + m.p99Latency, 0) / metrics.length;

  const throughputVariance = (maxThroughput - minThroughput) / avgThroughput;
  const stableWindows = countStableWindows(metrics);

  console.log(`
====================================
RELIABILITY TEST RESULTS
====================================
Average Throughput: ${avgThroughput.toFixed(2)} RPS
Throughput Variance: ${(throughputVariance * 100).toFixed(2)}%
Average Error Rate: ${(avgErrorRate * 100).toFixed(2)}%
Average P99 Latency: ${avgP99Latency.toFixed(2)}ms
Stable Measurement Windows: ${stableWindows}/${metrics.length}

Test Status: ${
    evaluateTestSuccess(avgThroughput, throughputVariance, avgErrorRate, avgP99Latency, stableWindows)
      ? "PASSED"
      : "FAILED"
  }
====================================
`);
}

function countStableWindows(metrics) {
  return metrics.filter(isStableWindow).length;
}

function isStableWindow(metrics) {
  const lowerBound = TEST_CONFIG.TARGET_LOAD * 0.9;
  const upperBound = TEST_CONFIG.TARGET_LOAD * (1 + TEST_CONFIG.MAX_THROUGHPUT_VARIANCE);

  return (
    metrics.errorRate <= TEST_CONFIG.MAX_ERROR_RATE &&
    metrics.p99Latency <= TEST_CONFIG.MAX_P99_LATENCY &&
    metrics.currentThroughput >= lowerBound &&
    metrics.currentThroughput <= upperBound
  );
}

function evaluateTestSuccess(avgThroughput, throughputVariance, avgErrorRate, avgP99Latency, stableWindows) {
  return (
    throughputVariance <= TEST_CONFIG.MAX_THROUGHPUT_VARIANCE &&
    avgErrorRate <= TEST_CONFIG.MAX_ERROR_RATE &&
    avgP99Latency <= TEST_CONFIG.MAX_P99_LATENCY &&
    stableWindows >= TEST_CONFIG.REQUIRED_STABLE_WINDOWS
  );
}
