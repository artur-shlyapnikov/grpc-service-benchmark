package org.example.perf.grpc;

import io.grpc.examples.helloworld.HelloRequest;
import org.example.perf.grpc.impl.GreeterServiceCall;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.perf.grpc.core.DslGrpcSampler.grpcSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

@Tag("load")
class GrpcLoadTest {

    private static final int TARGET_USERS = 10;
    private static final Duration RAMP_UP_TIME = Duration.ofSeconds(30);
    private static final Duration TEST_DURATION = Duration.ofMinutes(5);
    private static final long MAX_P99_LATENCY_MS = 1000;
    private static final double MAX_ERROR_RATE = 0.01; // 1%

    @Test
    void loadTest() throws Exception {
        HelloRequest request = HelloRequest.newBuilder()
                .setName("World")
                .build();

        String testId = Instant.now().toString();
        String resultsPath = String.format("target/load-test-%s.jtl", testId);

        TestPlanStats stats = testPlan(
                threadGroup("GRPC Load Test Thread Group")
                        .rampToAndHold(TARGET_USERS, RAMP_UP_TIME, TEST_DURATION)
                        .children(
                                grpcSampler(new GreeterServiceCall())
                                        .host("localhost")
                                        .port(50052)
                                        .usePlaintext()
                                        .request(request)
                        ),
                jtlWriter(resultsPath)
                        .withAllFields()
        ).run();

        // extract key metrics
        long totalRequests = stats.overall().samplesCount();
        long totalErrors = stats.overall().errorsCount();
        double errorRate = (double) totalErrors / totalRequests;
        Duration p99Latency = stats.overall().sampleTime().perc99();

        // log results
        System.out.printf("""
                Load Test Results (Test ID: %s)
                ----------------------------------------
                Total Requests: %d
                Total Errors: %d
                Error Rate: %.2f%%
                Response Time Statistics:
                  Mean: %d ms
                  P90: %d ms
                  P95: %d ms
                  P99: %d ms
                ----------------------------------------
                Results file: %s
                """,
                testId,
                totalRequests,
                totalErrors,
                errorRate * 100,
                stats.overall().sampleTime().mean().toMillis(),
                stats.overall().sampleTime().perc90().toMillis(),
                stats.overall().sampleTime().perc95().toMillis(),
                p99Latency.toMillis(),
                resultsPath
        );

        assertThat(p99Latency.toMillis())
                .as("99th percentile response time")
                .isLessThan(MAX_P99_LATENCY_MS);

        assertThat(errorRate)
                .as("Error rate")
                .isLessThan(MAX_ERROR_RATE);
    }
}