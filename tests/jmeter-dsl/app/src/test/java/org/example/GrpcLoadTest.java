package org.example;

import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;
import java.time.Duration;

import static org.example.perf.grpc.sampler.DslGrpcSampler.grpcSampler;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

@Tag("performance") // we don't want to run this on each build
class GrpcLoadTest {

    @Test
    void testGreeterService() throws Exception {
        HelloRequest request = HelloRequest.newBuilder()
                .setName("World")
                .build();

        TestPlanStats stats = testPlan(
                threadGroup()
                        .rampToAndHold(2, Duration.ofSeconds(10), Duration.ofMinutes(1))
                        .children(
                                grpcSampler()
                                        .host("localhost")
                                        .port(50052)
                                        .usePlaintext()
                                        .methodName("helloworld.Greeter/SayHello")
                                        .request(request)
                        ),
                jtlWriter("target/grpc_results.jtl")
                        .saveAsXml(true)
                        .withElapsedTime(true)
                        .withLatency(true)
                        .withResponseCode(true)
                        .withResponseMessage(true)
                        .withSuccess(true)
                        .withActiveThreadCounts(true)
        ).run();

        // show stats
        System.out.printf("Average response time: %d ms%n",
                stats.overall().sampleTime().mean().toMillis());
        System.out.printf("Error rate: %.2f%%%n",
                stats.overall().errors().total() * 100d / stats.overall().samples().total());
        System.out.printf("Total samples: %d%n",
                stats.overall().samples().total());
    }
}