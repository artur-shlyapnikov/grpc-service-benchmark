package org.example.perf.grpc;

import static org.example.perf.grpc.core.DslGrpcSampler.grpcSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

import io.grpc.examples.helloworld.HelloRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.example.perf.grpc.impl.GreeterServiceCall;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import java.util.concurrent.TimeUnit;

/**
 * Reliability test for gRPC service at sustained load.
 * This test verifies the system's ability to maintain stable performance
 * under a constant load over an extended period.
 */
class ReliabilityTest {
    private static final Logger log = LoggerFactory.getLogger(ReliabilityTest.class);

    private static final class TestConfig {
        static final int TARGET_LOAD = 10743;
        static final int THREAD_MULTIPLIER = 4; // Multiplier to ensure enough threads
        // Calculate required threads based on target load and expected response time
        static final int MAX_THREADS = (int)(TARGET_LOAD * 1.5 * THREAD_MULTIPLIER); // Allow for 50% overhead plus multiplier

        static final Duration TEST_DURATION = Duration.ofMinutes(30);
        static final Duration RAMP_UP_DURATION = Duration.ofMinutes(5);
        static final Duration MEASUREMENT_WINDOW = Duration.ofMinutes(5);

        static final double MAX_ERROR_RATE = 0.01; // 1% strict error criteria since the service is simple

        static final double MAX_THROUGHPUT_VARIANCE = 0.20; // 20%

        // maximum acceptable P99 latency (ms)
        static final double MAX_P99_LATENCY = 900.0;

        // minimum successful measurement windows required
        static final int REQUIRED_STABLE_WINDOWS = 6;
    }

    private static final String TEST_HOST = "162.55.34.236";
    private static final String INFLUX_URL = "http://162.55.34.236:8086/write?db=perf-tests";
    private static final int TEST_PORT = 50052;

    // reusing records from MaximumLoadTest
    private record LoadMetrics(
            double errorRate,
            double p99Latency,
            double currentThroughput,
            Duration measurementTime
    ) {}

    private record StabilityWindow(
            double minThroughput,
            double maxThroughput,
            double avgThroughput,
            int sampleCount
    ) {}

    @Tag("reliability")
    @Test
    @Timeout(value = 70, unit = TimeUnit.MINUTES)
    void verifyReliability() throws Exception {
        String testId = "reliability-" + Instant.now();
        HelloRequest request = HelloRequest.newBuilder().setName("World").build();

        log.info("Starting reliability test at {} RPS with {} max threads",
            TestConfig.TARGET_LOAD, TestConfig.MAX_THREADS);
        log.info("Test duration: {} minutes", TestConfig.TEST_DURATION.toMinutes());

        List<LoadMetrics> allMetrics = runReliabilityTest(request, testId);
        analyzeResults(allMetrics);
    }

    private List<LoadMetrics> runReliabilityTest(HelloRequest request, String testId)
            throws Exception {
        List<LoadMetrics> allMetrics = new ArrayList<>();
        Instant testStart = Instant.now();

        TestPlanStats stats = testPlan(
                rpsThreadGroup()
                        .maxThreads(TestConfig.MAX_THREADS)
                        .rampTo(TestConfig.TARGET_LOAD, TestConfig.RAMP_UP_DURATION)
                        .holdFor(TestConfig.TEST_DURATION)
                        .children(
                                grpcSampler(new GreeterServiceCall())
                                        .host(TEST_HOST)
                                        .port(TEST_PORT)
                                        .usePlaintext()
                                        .request(request)
                        ),
                influxDbListener(INFLUX_URL)
                        .token("my-super-secret-auth-token")
        ).run();

        Duration elapsed = Duration.ZERO;
        while (elapsed.compareTo(TestConfig.TEST_DURATION) < 0) {
            LoadMetrics metrics = calculateMetrics(stats, testStart.plus(elapsed));
            allMetrics.add(metrics);
            elapsed = elapsed.plus(TestConfig.MEASUREMENT_WINDOW);
        }

        return allMetrics;
    }

    private void analyzeResults(List<LoadMetrics> metrics) {
        log.info("\n====================================");
        log.info("RELIABILITY TEST RESULTS");
        log.info("====================================");

        // Calculate overall statistics
        double avgThroughput = metrics.stream()
                .mapToDouble(LoadMetrics::currentThroughput)
                .average()
                .orElse(0.0);

        double maxThroughput = metrics.stream()
                .mapToDouble(LoadMetrics::currentThroughput)
                .max()
                .orElse(0.0);

        double minThroughput = metrics.stream()
                .mapToDouble(LoadMetrics::currentThroughput)
                .min()
                .orElse(0.0);

        double avgErrorRate = metrics.stream()
                .mapToDouble(LoadMetrics::errorRate)
                .average()
                .orElse(0.0);

        double avgP99Latency = metrics.stream()
                .mapToDouble(LoadMetrics::p99Latency)
                .average()
                .orElse(0.0);

        // Calculate stability metrics
        double throughputVariance = (maxThroughput - minThroughput) / avgThroughput;
        int stableWindows = countStableWindows(metrics);

        // Log results
        log.info("Average Throughput: {} RPS", String.format("%.2f", avgThroughput));
        log.info("Throughput Variance: {}%", String.format("%.2f", throughputVariance * 100));
        log.info("Average Error Rate: {}%", String.format("%.2f", avgErrorRate * 100));
        log.info("Average P99 Latency: {}ms", String.format("%.2f", avgP99Latency));
        log.info("Stable Measurement Windows: {}/{}", stableWindows, metrics.size());

        // Evaluate test success
        boolean isSuccessful = evaluateTestSuccess(avgThroughput, throughputVariance,
                avgErrorRate, avgP99Latency, stableWindows);

        log.info("\nTest Status: {}", isSuccessful ? "PASSED" : "FAILED");
        log.info("====================================");

        if (!isSuccessful) {
            throw new AssertionError("Reliability test failed to meet stability criteria");
        }
    }

    private int countStableWindows(List<LoadMetrics> metrics) {
        int stableWindows = 0;
        for (LoadMetrics metric : metrics) {
            if (isStableWindow(metric)) {
                stableWindows++;
            }
        }
        return stableWindows;
    }

    private boolean isStableWindow(LoadMetrics metrics) {
        double lowerBound = TestConfig.TARGET_LOAD * 0.80; // allow 20% lower than target
        double upperBound = TestConfig.TARGET_LOAD * (1 + TestConfig.MAX_THROUGHPUT_VARIANCE);

        return metrics.errorRate() <= TestConfig.MAX_ERROR_RATE
                && metrics.p99Latency() <= TestConfig.MAX_P99_LATENCY
                && metrics.currentThroughput() >= lowerBound
                && metrics.currentThroughput() <= upperBound;
    }

    private boolean evaluateTestSuccess(double avgThroughput, double throughputVariance,
                                        double avgErrorRate, double avgP99Latency,
                                        int stableWindows) {
        return throughputVariance <= TestConfig.MAX_THROUGHPUT_VARIANCE
                && avgErrorRate <= TestConfig.MAX_ERROR_RATE
                && avgP99Latency <= TestConfig.MAX_P99_LATENCY
                && stableWindows >= TestConfig.REQUIRED_STABLE_WINDOWS;
    }

    private LoadMetrics calculateMetrics(TestPlanStats stats, Instant measurementTime) {
        return new LoadMetrics(
                (double) stats.overall().errorsCount() / stats.overall().samplesCount(),
                stats.overall().sampleTime().perc99().toMillis(),
                stats.overall().samples().perSecond(),
                Duration.between(measurementTime, Instant.now())
        );
    }
}