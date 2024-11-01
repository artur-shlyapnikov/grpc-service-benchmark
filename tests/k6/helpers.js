export function calculateMetrics(data, startTime) {
  const currentTime = Date.now();
  return {
    errorRate: data.errors / (data.checks || 1),
    p99Latency: data.latency.p(99),
    currentThroughput: data.iterations / ((currentTime - startTime) / 1000),
    measurementTime: currentTime - startTime,
  };
}

export function calculateStabilityWindow(measurements) {
  if (!measurements.length) return null;

  const throughputs = measurements.map((m) => m.currentThroughput);
  const min = Math.min(...throughputs);
  const max = Math.max(...throughputs);
  const avg = throughputs.reduce((a, b) => a + b, 0) / throughputs.length;

  // Check if variance is within stability threshold
  if ((max - min) / avg > 0.1) {
    // 10% threshold
    return null;
  }

  return {
    minThroughput: min,
    maxThroughput: max,
    avgThroughput: avg,
    sampleCount: measurements.length,
  };
}
