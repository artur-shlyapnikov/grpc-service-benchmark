package org.example.perf.grpc;

import static org.assertj.core.api.Assertions.assertThat;
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
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

@Tag("load")
class MaximumLoadTest {
    private static final double ERROR_THRESHOLD = 0.001; // 0.1%
    private static final double LATENCY_MULTIPLIER_THRESHOLD = 10.0;
    private static final double THROUGHPUT_PLATEAU_THRESHOLD = 0.05; // 5% improvement

    private static final Duration STEP_DURATION = Duration.ofMinutes(2);
    private static final Duration BASELINE_DURATION = Duration.ofMinutes(3);
    private static final Duration VALIDATION_DURATION = Duration.ofMinutes(10);

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 50052;

    private record TestResult(int targetRate, TestPlanStats stats) {}
    private record MaxLoadResult(int maxRate, double baselineP99, List<TestResult> results) {}

    @Test
    void findMaximumLoad() throws Exception {
        String testId = Instant.now().toString();
        HelloRequest request = HelloRequest.newBuilder()
                .setName("World")
                .build();

        // run baseline test
        TestPlanStats baseline = runLoadTest(request, 100, BASELINE_DURATION, testId + "-baseline");
        double baselineP99 = baseline.overall().sampleTime().perc99().toMillis();

        List<TestResult> results = new ArrayList<>();
        results.add(new TestResult(100, baseline));

        // find maximum load using step loading
        MaxLoadResult maxLoad = findMaxLoad(request, baselineP99, results, testId);

        validateResults(request, maxLoad, testId);

        generateLoadTestReport(maxLoad);
    }

    private MaxLoadResult findMaxLoad(HelloRequest request, double baselineP99,
                                      List<TestResult> results, String testId) throws Exception {

        int currentRate = 200; // start with doubling from baseline
        boolean foundFirstSaturation = false;
        Double previousThroughput = null;

        while (true) {
            TestPlanStats stats = runLoadTest(request, currentRate, STEP_DURATION,
                    testId + "-step-" + currentRate);
            results.add(new TestResult(currentRate, stats));

            // Calculate metrics
            double errorRate = (double) stats.overall().errorsCount() /
                    stats.overall().samplesCount();
            double p99Latency = stats.overall().sampleTime().perc99().toMillis();
            double currentThroughput = stats.overall().samples().perSecond();

            // check stopping criteria
            if (errorRate > ERROR_THRESHOLD) {
                System.out.printf("Stopping: Error rate %.2f%% exceeded threshold %.2f%%\n",
                        errorRate * 100, ERROR_THRESHOLD * 100);
                break;
            }

            if (p99Latency > baselineP99 * LATENCY_MULTIPLIER_THRESHOLD) {
                System.out.printf("Stopping: P99 latency %.2fms exceeded %dx baseline %.2fms\n",
                        p99Latency, LATENCY_MULTIPLIER_THRESHOLD, baselineP99);
                break;
            }

            if (previousThroughput != null) {
                double improvement = (currentThroughput - previousThroughput) / previousThroughput;
                if (improvement < THROUGHPUT_PLATEAU_THRESHOLD) {
                    System.out.printf("Stopping: Throughput improvement %.2f%% below threshold %.2f%%\n",
                            improvement * 100, THROUGHPUT_PLATEAU_THRESHOLD * 100);
                    break;
                }
            }

            previousThroughput = currentThroughput;

            // Determine next step
            if (!foundFirstSaturation && (errorRate > ERROR_THRESHOLD / 2 ||
                    p99Latency > baselineP99 * LATENCY_MULTIPLIER_THRESHOLD / 2)) {
                foundFirstSaturation = true;
            }

            currentRate += foundFirstSaturation ?
                    (int)(currentRate * 0.2) : // 20% increment after first saturation
                    currentRate;               // double until first saturation
        }

        return new MaxLoadResult(currentRate, baselineP99, results);
    }

    private void validateResults(HelloRequest request, MaxLoadResult maxLoad, String testId)
            throws Exception {
        int maxRate = maxLoad.maxRate();

        // test at 90% of maximum
        System.out.println("Validating at 90% load...");
        TestPlanStats test90 = runLoadTest(request, (int)(maxRate * 0.9),
                VALIDATION_DURATION, testId + "-validate-90");
        assertAcceptableResults(test90, maxLoad.baselineP99());

        // test at 100% of maximum
        System.out.println("Validating at 100% load...");
        TestPlanStats test100 = runLoadTest(request, maxRate,
                Duration.ofMinutes(5), testId + "-validate-100");
        assertAcceptableResults(test100, maxLoad.baselineP99());

        // test at 110% of maximum
        System.out.println("Validating at 110% load...");
        TestPlanStats test110 = runLoadTest(request, (int)(maxRate * 1.1),
                Duration.ofMinutes(3), testId + "-validate-110");
        // we expect this might fail, but we want to see by how much
    }

    private TestPlanStats runLoadTest(HelloRequest request, int targetRate,
                                      Duration duration, String testId) throws Exception {
        String resultsPath = String.format("target/load-test-%s.jtl", testId);

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
                jtlWriter(resultsPath)
                        .withAllFields()
        ).run();
    }

    private void assertAcceptableResults(TestPlanStats stats, double baselineP99) {
        double errorRate = (double) stats.overall().errorsCount() /
                stats.overall().samplesCount();
        double p99Latency = stats.overall().sampleTime().perc99().toMillis();

        assertThat(errorRate)
                .as("Error rate")
                .isLessThan(ERROR_THRESHOLD);

        assertThat(p99Latency)
                .as("P99 latency")
                .isLessThan(baselineP99 * LATENCY_MULTIPLIER_THRESHOLD);
    }

    private void generateLoadTestReport(MaxLoadResult maxLoad) {
        System.out.println("\nLoad Test Results Summary");
        System.out.println("========================");
        System.out.printf("Maximum sustainable load: %d RPS\n", maxLoad.maxRate());
        System.out.printf("Baseline P99 latency: %.2f ms\n", maxLoad.baselineP99());
        System.out.println("\nDetailed Results:");
        System.out.println("----------------");

        maxLoad.results().forEach(result -> {
            TestPlanStats stats = result.stats();
            System.out.printf("""
                Target Rate: %d RPS
                - Throughput: %.2f RPS
                - Error Rate: %.2f%%
                - P99 Latency: %.2f ms
                - Mean Latency: %.2f ms
                """,
                    result.targetRate(),
                    stats.overall().samples().perSecond(),
                    ((double) stats.overall().errorsCount() / stats.overall().samplesCount()) * 100,
                    stats.overall().sampleTime().perc99().toMillis(),
                    stats.overall().sampleTime().mean().toMillis()
            );
        });
    }
}