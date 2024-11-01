import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";
import grpc from "k6/net/grpc";

const ThroughputTrend = new Trend("throughput");
const LatencyTrend = new Trend("latency");
const ErrorRate = new Rate("errors");
const RequestCount = new Counter("requests");

const CONFIG = {
  INITIAL_LOAD: 1000,
  LARGE_STEP: 2000,
  SMALL_STEP: 200,
  STEP_DURATION: 300, // 5 minutes in seconds
  STABILITY_CHECK_DURATION: 120, // 2 minutes in seconds
  STABILITY_THRESHOLD: 0.05,
  REQUIRED_STABLE_MEASUREMENTS: 3,
  MIN_EFFICIENCY_RATIO: 0.95,
  MIN_THROUGHPUT_GROWTH_RATIO: 1.1,
  TARGET_HOST: "localhost:50051",
  PROTO_PATH: "./proto/helloworld.proto",
};

const measurements = new SharedArray("measurements", function () {
  return [];
});

export function setup() {
  const client = new grpc.Client();
  client.load([], CONFIG.PROTO_PATH);
  return { client };
}

function calculateMetrics(duration) {
  return {
    errorRate: ErrorRate.value,
    p99Latency: LatencyTrend.p(99),
    currentThroughput: RequestCount.value / duration,
    measurementTime: new Date().getTime(),
  };
}

function calculateStabilityWindow(metrics) {
  if (!metrics.length) return null;

  const throughputs = metrics.map((m) => m.currentThroughput);
  const min = Math.min(...throughputs);
  const max = Math.max(...throughputs);
  const avg = throughputs.reduce((a, b) => a + b) / throughputs.length;

  if ((max - min) / avg > CONFIG.STABILITY_THRESHOLD) {
    return null;
  }

  return {
    minThroughput: min,
    maxThroughput: max,
    avgThroughput: avg,
    sampleCount: metrics.length,
  };
}

function isEfficient(stability, targetRate) {
  return stability.avgThroughput >= targetRate * CONFIG.MIN_EFFICIENCY_RATIO;
}

function runLoadTest(client, targetRate, duration) {
  const startTime = new Date().getTime();
  const requestsPerSec = targetRate / exec.instance.vusActive;
  const interval = 1000 / requestsPerSec;

  while (new Date().getTime() - startTime < duration * 1000) {
    const iterationStart = new Date().getTime();

    const response = client.invoke("helloworld.Greeter/SayHello", {
      name: "World",
    });

    check(response, {
      "status is OK": (r) => r && r.status === grpc.StatusOK,
    });

    if (response.status !== grpc.StatusOK) {
      ErrorRate.add(1);
    }

    LatencyTrend.add(new Date().getTime() - iterationStart);
    RequestCount.add(1);

    // Rate limiting
    const iterationTime = new Date().getTime() - iterationStart;
    if (iterationTime < interval) {
      sleep((interval - iterationTime) / 1000);
    }
  }
}

function findApproximateMaximum(client) {
  let currentRate = CONFIG.INITIAL_LOAD;
  let lastStableThroughput = 0;

  while (true) {
    const metrics = [];
    runLoadTest(client, currentRate, CONFIG.STEP_DURATION);
    metrics.push(calculateMetrics(CONFIG.STEP_DURATION));

    for (let i = 0; i < CONFIG.REQUIRED_STABLE_MEASUREMENTS - 1; i++) {
      runLoadTest(client, currentRate, CONFIG.STABILITY_CHECK_DURATION);
      metrics.push(calculateMetrics(CONFIG.STABILITY_CHECK_DURATION));
    }

    const stability = calculateStabilityWindow(metrics);

    if (!stability) {
      return currentRate - CONFIG.LARGE_STEP;
    }

    if (
      lastStableThroughput > 0 &&
      stability.avgThroughput < lastStableThroughput * CONFIG.MIN_THROUGHPUT_GROWTH_RATIO
    ) {
      return currentRate;
    }

    lastStableThroughput = stability.avgThroughput;
    currentRate += CONFIG.LARGE_STEP;
  }
}

function findPreciseMaximum(client, approximateMax) {
  let left = Math.max(CONFIG.INITIAL_LOAD, approximateMax - CONFIG.LARGE_STEP);
  let right = approximateMax + CONFIG.LARGE_STEP;

  while (right - left > CONFIG.SMALL_STEP) {
    const mid = Math.floor((left + right) / 2);
    const metrics = [];

    runLoadTest(client, mid, CONFIG.STEP_DURATION);
    metrics.push(calculateMetrics(CONFIG.STEP_DURATION));

    for (let i = 0; i < CONFIG.REQUIRED_STABLE_MEASUREMENTS - 1; i++) {
      runLoadTest(client, mid, CONFIG.STABILITY_CHECK_DURATION);
      metrics.push(calculateMetrics(CONFIG.STABILITY_CHECK_DURATION));
    }

    const stability = calculateStabilityWindow(metrics);

    if (stability && isEfficient(stability, mid)) {
      left = mid;
    } else {
      right = mid;
    }
  }

  return left;
}

export default function () {
  const { client } = exec.vu.data;
  client.connect(CONFIG.TARGET_HOST);

  console.log("Starting maximum load test...");

  // Phase 1: Quick approximation
  const approximateMax = findApproximateMaximum(client);
  console.log(`Found approximate maximum at ${approximateMax} RPS`);

  // Phase 2: Precise search
  const preciseMax = findPreciseMaximum(client, approximateMax);
  console.log(`Found precise maximum at ${preciseMax} RPS`);

  // Phase 3: Final verification
  const metrics = [];
  runLoadTest(client, preciseMax, CONFIG.STEP_DURATION);
  metrics.push(calculateMetrics(CONFIG.STEP_DURATION));

  const stability = calculateStabilityWindow(metrics);
  const sustainableRate = Math.floor(stability.avgThroughput * 0.8);

  console.log("\n====================================");
  console.log("MAXIMUM LOAD TEST RESULTS");
  console.log("====================================");
  console.log(`Verified maximum throughput: ${stability.avgThroughput.toFixed(2)} RPS`);
  console.log(`At target rate: ${preciseMax} RPS`);
  console.log(
    `Throughput stability: ${(
      ((stability.maxThroughput - stability.minThroughput) / stability.avgThroughput) *
      100
    ).toFixed(2)}%`
  );
  console.log(`\nRecommended sustainable load: ${sustainableRate} RPS`);
  console.log("(80% of maximum stable throughput)");
  console.log("====================================");

  client.close();
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: " ", enableColors: true }),
    "summary.json": JSON.stringify(data),
  };
}
