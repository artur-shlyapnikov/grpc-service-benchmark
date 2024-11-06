package org.example.perf.grpc;

import static org.example.perf.grpc.core.DslGrpcSampler.grpcSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

import io.grpc.examples.helloworld.HelloRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.example.perf.grpc.impl.GreeterServiceCall;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

class MaximumLoadTest {
    private static final Logger log = LoggerFactory.getLogger(MaximumLoadTest.class);
    private static final String TEST_HOST = "162.55.34.236";
    private static final String INFLUX_URL = "http://162.55.34.236:8086/write?db=perf-tests";
    private static final int TEST_PORT = 50052;

    private static final class TestConfig {
        static final int INITIAL_LOAD = 1000;
        static final int STEP = 500;
        static final Duration STEP_DURATION = Duration.ofSeconds(60);

        // Thresholds for stopping the test
        static final double MAX_ERROR_RATE = 0.01; // 1%
        static final double LATENCY_MULTIPLIER_THRESHOLD = 2.0; // 2x from baseline
    }

    private record TestResult(
            double errorRate,
            double p99Latency,
            double throughput
    ) {}

    @Tag("load")
    @Test
    @Timeout(value = 55, unit = TimeUnit.MINUTES)
    void findMaximumLoad() throws Exception {
        int currentRate = TestConfig.INITIAL_LOAD;
        TestResult baseline = null;
        TestResult lastStable = null;

        while (true) {
            TestResult result = runLoadTest(currentRate);

            // Capture baseline metrics from first successful run
            if (baseline == null && result.errorRate < TestConfig.MAX_ERROR_RATE) {
                baseline = result;
            }

            log.info("Load: {} RPS, Errors: {}, P99: {} ms",
                    currentRate,
                    String.format("%.2f%%", result.errorRate * 100),
                    String.format("%.2f", result.p99Latency));

            // Check stopping conditions
            if (shouldStop(result, baseline)) {
                break;
            }

            lastStable = result;
            currentRate += TestConfig.STEP;
        }

        logResults(lastStable, currentRate - TestConfig.STEP);
    }

    private TestResult runLoadTest(int targetRate) throws Exception {
        TestPlanStats stats = testPlan(
                threadGroup()
                        .rampTo(targetRate, Duration.ofSeconds(30))
                        .holdFor(TestConfig.STEP_DURATION)
                        .children(
                                grpcSampler(new GreeterServiceCall())
                                        .host(TEST_HOST)
                                        .port(TEST_PORT)
                                        .usePlaintext()
                                        .request(HelloRequest.newBuilder().setName("World").build())
                        ),
                influxDbListener(INFLUX_URL)
                        .token("my-super-secret-auth-token")
        ).run();

        return new TestResult(
                (double) stats.overall().errorsCount() / stats.overall().samplesCount(),
                stats.overall().sampleTime().perc99().toMillis(),
                stats.overall().samples().perSecond()
        );
    }

    private boolean shouldStop(TestResult current, TestResult baseline) {
        if (current.errorRate > TestConfig.MAX_ERROR_RATE) {
            log.info("Stopping: Error rate threshold exceeded");
            return true;
        }

        // Only check latency degradation if we have a baseline
        if (baseline != null &&
                current.p99Latency > baseline.p99Latency * TestConfig.LATENCY_MULTIPLIER_THRESHOLD) {
            log.info("Stopping: Latency degradation detected");
            return true;
        }

        return false;
    }

    private void logResults(TestResult lastStable, int maxRate) {
        log.info("\n=== Test Results ===");
        log.info("Maximum stable load: {} RPS", maxRate);
        log.info("Error rate: {}%", String.format("%.2f", lastStable.errorRate * 100));
        log.info("P99 latency: {} ms", String.format("%.2f", lastStable.p99Latency));
        log.info("Actual throughput: {} RPS", String.format("%.2f", lastStable.throughput));
    }
}