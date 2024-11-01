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
 * This test implements an advanced maximum load search algorithm that addresses several
 * key challenges in performance testing:
 *
 * 1. False Maximums: Systems often show temporary throughput drops that can be mistaken
 *    for the maximum. We use multiple measurements and stability checks to avoid this.
 *
 * 2. Search Efficiency: A pure linear search is slow and might miss the true maximum.
 *    We use a three-phase approach: quick search, binary search, and verification.
 *
 * 3. Stability Verification: A single measurement isn't enough to confirm system capacity.
 *    We require multiple stable measurements within a defined variance threshold.
 */
class MaximumLoadTest {
    private static final Logger log = LoggerFactory.getLogger(MaximumLoadTest.class);

    private static final class TestConfig {
        // Initial load is low to allow system warm-up and baseline measurement
        static final int INITIAL_LOAD = 1000;

        // Large steps for quick approximate search - we want to find the general area fast
        // but not so large that we miss major behavior changes
        static final int LARGE_STEP = 1000;

        // Small steps for precise search - should be about 10% of LARGE_STEP to provide
        // good resolution during binary search
        static final int SMALL_STEP = 500;

        // Main measurement duration - must be long enough for system to stabilize
        // and show consistent behavior
        static final Duration STEP_DURATION = Duration.ofSeconds(60);

        // Stability check duration - shorter than main measurement but long enough
        // to detect inconsistencies
        static final Duration STABILITY_CHECK_DURATION = Duration.ofSeconds(30);

        // Acceptable throughput variance - 5% allows for normal system fluctuations
        // while catching significant instability
        static final double STABILITY_THRESHOLD = 0.05;

        // Multiple measurements reduce the chance of false positives/negatives
        static final int REQUIRED_STABLE_MEASUREMENTS = 2;

        // Minimum efficiency ratio - if we can't achieve this, the load is too high
        static final double MIN_EFFICIENCY_RATIO = 0.95;

        // How much throughput should grow with load increase - if growth is less,
        // we're approaching maximum
        static final double MIN_THROUGHPUT_GROWTH_RATIO = 1.1; // 10% growth
    }

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 50052;
    private static final String INFLUX_URL = "http://localhost:8086/write?db=perf-tests";

    /**
     * Represents a single measurement point. We track both metrics and timing
     * to analyze stability over time.
     */
    private record LoadMetrics(
            double errorRate,
            double p99Latency,
            double currentThroughput,
            Duration measurementTime
    ) {}

    /**
     * Represents a stability measurement window. By tracking min/max/avg,
     * we can detect unstable behavior even if individual measurements look good.
     */
    private record StabilityWindow(
            double minThroughput,
            double maxThroughput,
            double avgThroughput,
            int sampleCount
    ) {}

    @Tag("load")
    @Test
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    void findMaximumLoad() throws Exception {
        String testId = Instant.now().toString();
        HelloRequest request = HelloRequest.newBuilder().setName("World").build();

        // Phase 1: Quick approximation
        // This gets us to the right ballpark quickly. We use large steps and look for
        // significant changes in throughput growth rate.
        int approximateMax = findApproximateMaximum(request, testId);
        log.info("Found approximate maximum at {} RPS", approximateMax);

        // Phase 2: Precise search
        // Binary search around the approximate maximum lets us efficiently find
        // the exact point where stability breaks down.
        int preciseMax = findPreciseMaximum(request, approximateMax, testId);
        log.info("Found precise maximum at {} RPS", preciseMax);

        // Phase 3: Verification
        // Final verification ensures our found maximum is truly stable
        // and wasn't a lucky measurement.
        verifyMaximum(request, preciseMax, testId);
    }

    private int findApproximateMaximum(HelloRequest request, String testId) throws Exception {
        int currentRate = TestConfig.INITIAL_LOAD;
        double lastStableThroughput = 0;

        while (true) {
            StabilityWindow stability = measureWithStabilityCheck(request, currentRate, testId);

            // No stable measurements = we've gone too far
            if (stability == null) {
                return currentRate - TestConfig.LARGE_STEP;
            }

            // Throughput growth slowing down = approaching maximum
            if (lastStableThroughput > 0 &&
                    stability.avgThroughput() < lastStableThroughput * TestConfig.MIN_THROUGHPUT_GROWTH_RATIO) {
                return currentRate;
            }

            lastStableThroughput = stability.avgThroughput();
            currentRate += TestConfig.LARGE_STEP;
        }
    }

    private int findPreciseMaximum(HelloRequest request, int approximateMax, String testId)
            throws Exception {
        // Search range: one step below and above our approximation
        int left = Math.max(TestConfig.INITIAL_LOAD, approximateMax - TestConfig.LARGE_STEP);
        int right = approximateMax + TestConfig.LARGE_STEP;

        // Binary search until we're within our small step size
        while (right - left > TestConfig.SMALL_STEP) {
            int mid = (left + right) / 2;
            StabilityWindow stability = measureWithStabilityCheck(request, mid, testId);

            // If stable and efficient, we can go higher
            if (stability != null && isEfficient(stability, mid)) {
                left = mid;
            } else {
                // Otherwise, we need to search lower
                right = mid;
            }
        }

        return left; // Highest stable rate we found
    }

    private StabilityWindow measureWithStabilityCheck(HelloRequest request, int targetRate,
                                                      String testId) throws Exception {
        List<LoadMetrics> measurements = new ArrayList<>();

        // initial measurement
        TestPlanStats stats = runLoadTest(request, targetRate, TestConfig.STEP_DURATION,
                testId + "-step-" + targetRate);
        LoadMetrics initial = calculateMetrics(stats, Instant.now());
        measurements.add(initial);

        // additional stability measurements
        for (int i = 0; i < TestConfig.REQUIRED_STABLE_MEASUREMENTS - 1; i++) {
            stats = runLoadTest(request, targetRate, TestConfig.STABILITY_CHECK_DURATION,
                    testId + "-stability-" + targetRate + "-" + i);
            measurements.add(calculateMetrics(stats, Instant.now()));
        }

        return calculateStabilityWindow(measurements);
    }

    private TestPlanStats runLoadTest(HelloRequest request, int targetRate,
                                      Duration duration, String testId) throws Exception {
        return testPlan(
                threadGroup()
                        .rampTo(targetRate, Duration.ofSeconds(30))
                        .holdFor(duration)
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
    }

    private LoadMetrics calculateMetrics(TestPlanStats stats, Instant measurementTime) {
        return new LoadMetrics(
                (double) stats.overall().errorsCount() / stats.overall().samplesCount(),
                stats.overall().sampleTime().perc99().toMillis(),
                stats.overall().samples().perSecond(),
                Duration.between(measurementTime, Instant.now())
        );
    }

    private StabilityWindow calculateStabilityWindow(List<LoadMetrics> measurements) {
        if (measurements.isEmpty()) return null;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        double sum = 0;

        for (LoadMetrics m : measurements) {
            min = Math.min(min, m.currentThroughput());
            max = Math.max(max, m.currentThroughput());
            sum += m.currentThroughput();
        }

        double avg = sum / measurements.size();

        // Check if variance is within our stability threshold
        if ((max - min) / avg > TestConfig.STABILITY_THRESHOLD) {
            return null; // Too much variance
        }

        return new StabilityWindow(min, max, avg, measurements.size());
    }

    private boolean isEfficient(StabilityWindow stability, int targetRate) {
        return stability.avgThroughput() >= targetRate * TestConfig.MIN_EFFICIENCY_RATIO;
    }

    private void verifyMaximum(HelloRequest request, int rate, String testId) throws Exception {
        log.info("Verifying maximum load at {} RPS", rate);

        int currentRate = rate;
        int retries = 1;

        StabilityWindow stability = measureWithStabilityCheck(request, rate, testId);

        while (stability == null && retries > 0) {
            currentRate = (int)(currentRate * 0.9);
            log.info("Verification failed, retrying with reduced rate: {} RPS", currentRate);
            stability = measureWithStabilityCheck(request, currentRate, testId);
            retries--;
        }

        if (stability == null) {
            log.error("Failed to verify stable maximum load. Last attempted rate: {} RPS", currentRate);
            return;
        }

        log.info("\n====================================");
        log.info("MAXIMUM LOAD TEST RESULTS");
        log.info("====================================");
        log.info("Verified maximum throughput: {} RPS", String.format("%.2f", stability.avgThroughput()));
        log.info("At target rate: {} RPS", rate);
        log.info("Throughput stability: {}%",
                String.format("%.2f",
                        (stability.maxThroughput() - stability.minThroughput()) / stability.avgThroughput() * 100));

        int sustainableRate = (int)(stability.avgThroughput() * 0.8);
        log.info("\nRecommended sustainable load: {} RPS", sustainableRate);
        log.info("(80% of maximum stable throughput)");
        log.info("====================================");
    }
}