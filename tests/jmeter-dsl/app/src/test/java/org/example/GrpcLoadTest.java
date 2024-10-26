package org.example;

import static org.assertj.core.api.Assertions.assertThat;
import static us.abstracta.jmeter.javadsl.JmeterDsl.*;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import us.abstracta.jmeter.javadsl.core.TestPlanStats;

// TODO: FIXME
@Tag("performance") // we don't want to run this on each build
class GrpcLoadTest {
    @Test
    @Tag("load")
    void findMaximumLoad() throws Exception {
        TestPlanStats stats = testPlan(
                threadGroup()
                        // warmup phase
                        .rampToAndHold(5, Duration.ofSeconds(10), Duration.ofSeconds(20))
                        // test phase
                        .rampTo(50, Duration.ofSeconds(30))
                        .children(
                                // since we don't have direct gRPC support yet, we'll use JSR223 Sampler
                                // TODO: check if we can use gRPC Sampler instead, it doesn't seems elegant
                                jsr223Sampler("gRPC Hello Request",
                                        """
                                        import io.grpc.ManagedChannelBuilder;
                                        import io.grpc.ManagedChannel;
                                        import java.util.concurrent.TimeUnit;
                                        import com.example.grpc.HelloRequest;
                                        import com.example.grpc.GreeterGrpc;
                                        
                                        // create channel
                                        ManagedChannel channel = ManagedChannelBuilder
                                            .forAddress("localhost", 50051)
                                            .usePlaintext()
                                            .build();
                                            
                                        try {
                                            // create stub
                                            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
                                            
                                            // create request
                                            HelloRequest request = HelloRequest.newBuilder()
                                                .setName("User-" + ctx.getThreadNum())
                                                .build();
                                                
                                            // make call and get response
                                            def response = stub.sayHello(request);
                                            
                                            // set response data
                                            SampleResult.setResponseData(response.toString(), "UTF-8");
                                            SampleResult.setSuccessful(true);
                                        } catch (Exception e) {
                                            SampleResult.setSuccessful(false);
                                            SampleResult.setResponseMessage(e.getMessage());
                                        } finally {
                                            channel.shutdown();
                                        }
                                        """),

                                // basic response verification
                                responseAssertion()
                                        .containsSubstrings("Hello User-")
                        ),

                // save results - using correct public methods
                jtlWriter("target/grpc_results")
                        .withAllFields(),  // This includes response data

                // generate HTML report
                // TODO: should be a separate reporter
                htmlReporter("target/grpc_report")
        ).run();

        // verify results using correct methods
        assertThat(stats.overall().errorsCount()).isZero();
        assertThat(stats.overall().sampleTime().perc95()).isLessThan(Duration.ofMillis(5000));
        assertThat(stats.overall().samplesCount()).isPositive();
    }
}