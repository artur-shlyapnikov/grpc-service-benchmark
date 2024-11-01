import { sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { Client } from "k6/net/grpc";
import { calculateMetrics, calculateStabilityWindow } from "./helpers.js";

const errorRate = new Rate("errors");
const callDuration = new Trend("call_duration");
const throughput = new Trend("throughput");

const TEST_CONFIG = {
  INITIAL_LOAD: 1000,
  LARGE_STEP: 1000,
  SMALL_STEP: 500,
  STEP_DURATION: 60,
  STABILITY_CHECK_DURATION: 30,
  STABILITY_THRESHOLD: 0.05,
  REQUIRED_STABLE_MEASUREMENTS: 2,
  MIN_EFFICIENCY_RATIO: 0.95,
  MIN_THROUGHPUT_GROWTH_RATIO: 1.1,
};

export const options = {
  thresholds: {
    "call_duration{kind:p99}": ["p(99)<500"],
    errors: ["rate<0.01"],
  },
};

const client = new Client();
client.load(["definitions"], "helloworld.proto");

export function setup() {
  console.log("Starting maximum load test");
  return { testId: Date.now().toString() };
}

export default function (data) {
  const result = findMaximumLoad(data.testId);
  console.log(`Maximum sustainable load found: ${result} RPS`);
}

function findMaximumLoad(testId) {
  // Phase 1: Quick approximation
  const approximateMax = findApproximateMaximum(testId);
  console.log(`Found approximate maximum at ${approximateMax} RPS`);

  // Phase 2: Precise search
  const preciseMax = findPreciseMaximum(approximateMax, testId);
  console.log(`Found precise maximum at ${preciseMax} RPS`);

  // Phase 3: Verification
  verifyMaximum(preciseMax, testId);

  return preciseMax;
}

function findApproximateMaximum(testId) {
  let currentRate = TEST_CONFIG.INITIAL_LOAD;
  let lastStableThroughput = 0;

  while (true) {
    const stability = measureWithStabilityCheck(currentRate, testId);

    if (!stability) {
      return currentRate - TEST_CONFIG.LARGE_STEP;
    }

    if (
      lastStableThroughput > 0 &&
      stability.avgThroughput < lastStableThroughput * TEST_CONFIG.MIN_THROUGHPUT_GROWTH_RATIO
    ) {
      return currentRate;
    }

    lastStableThroughput = stability.avgThroughput;
    currentRate += TEST_CONFIG.LARGE_STEP;
  }
}

function findPreciseMaximum(approximateMax, testId) {
  let left = Math.max(TEST_CONFIG.INITIAL_LOAD, approximateMax - TEST_CONFIG.LARGE_STEP);
  let right = approximateMax + TEST_CONFIG.LARGE_STEP;

  while (right - left > TEST_CONFIG.SMALL_STEP) {
    const mid = Math.floor((left + right) / 2);
    const stability = measureWithStabilityCheck(mid, testId);

    if (stability && isEfficient(stability, mid)) {
      left = mid;
    } else {
      right = mid;
    }
  }

  return left;
}

function measureWithStabilityCheck(targetRate, testId) {
  const measurements = [];

  // Initial measurement
  const startTime = Date.now();
  runLoadTest(targetRate, TEST_CONFIG.STEP_DURATION);
  measurements.push(
    calculateMetrics(
      {
        errors: errorRate.value,
        latency: callDuration,
        iterations: __VU * __ITER,
      },
      startTime
    )
  );

  // Stability measurements
  for (let i = 0; i < TEST_CONFIG.REQUIRED_STABLE_MEASUREMENTS - 1; i++) {
    const startTime = Date.now();
    runLoadTest(targetRate, TEST_CONFIG.STABILITY_CHECK_DURATION);
    measurements.push(
      calculateMetrics(
        {
          errors: errorRate.value,
          latency: callDuration,
          iterations: __VU * __ITER,
        },
        startTime
      )
    );
  }

  return calculateStabilityWindow(measurements);
}

function runLoadTest(targetRate, duration) {
  const startTime = Date.now();
  const endTime = startTime + duration * 1000;

  while (Date.now() < endTime) {
    const resp = client.invoke("helloworld.Greeter/SayHello", {
      name: "World",
    });

    check(resp, {
      "status is OK": (r) => r && r.status === grpc.StatusOK,
    });

    callDuration.add(resp.duration);
    errorRate.add(resp.status !== grpc.StatusOK);
    throughput.add(1);

    sleep(1.0 / targetRate);
  }
}

function isEfficient(stability, targetRate) {
  return stability.avgThroughput >= targetRate * TEST_CONFIG.MIN_EFFICIENCY_RATIO;
}

function verifyMaximum(rate, testId) {
  console.log(`Verifying maximum load at ${rate} RPS`);

  let currentRate = rate;
  let retries = 1;
  let stability = measureWithStabilityCheck(rate, testId);

  while (!stability && retries > 0) {
    currentRate = Math.floor(currentRate * 0.9);
    console.log(`Verification failed, retrying with reduced rate: ${currentRate} RPS`);
    stability = measureWithStabilityCheck(currentRate, testId);
    retries--;
  }

  if (!stability) {
    console.error(`Failed to verify stable maximum load. Last attempted rate: ${currentRate} RPS`);
    return;
  }

  console.log(`
====================================
MAXIMUM LOAD TEST RESULTS
====================================
Verified maximum throughput: ${stability.avgThroughput.toFixed(2)} RPS
At target rate: ${rate} RPS
Throughput stability: ${(((stability.maxThroughput - stability.minThroughput) / stability.avgThroughput) * 100).toFixed(
    2
  )}%

Recommended sustainable load: ${Math.floor(stability.avgThroughput * 0.8)} RPS
(80% of maximum stable throughput)
====================================
`);
}
