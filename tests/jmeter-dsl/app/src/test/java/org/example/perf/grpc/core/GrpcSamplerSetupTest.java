package org.example.perf.grpc.core;

import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.impl.GreeterServiceCall;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcSamplerSetupTest {
    private static Arguments baseArgs() {
        Arguments args = new Arguments();
        args.addArgument("serviceCallClass", GreeterServiceCall.class.getName());
        args.addArgument("methodName", "helloworld.Greeter/SayHello");
        args.addArgument("host", "localhost");
        args.addArgument("port", "50051");
        args.addArgument("usePlaintext", "true");
        return args;
    }

    @Test
    void resolvesSharedRequestWithoutJson() {
        HelloRequest shared = HelloRequest.newBuilder().setName("shared-user").build();
        Arguments args = baseArgs();
        args.addArgument("requestRef", GrpcSampler.shareRequest(shared));
        GrpcSampler<HelloRequest, HelloReply> sampler = new GrpcSampler<>();
        JavaSamplerContext context = new JavaSamplerContext(args);

        sampler.setupTest(context);
        try {
            SampleResult result = sampler.runTest(context);

            assertThat(result.getSamplerData()).isEqualTo("{\"name\":\"shared-user\"}");
        } finally {
            sampler.teardownTest(context);
        }
    }

    @Test
    void parsesJsonRequestFallback() {
        Arguments args = baseArgs();
        args.addArgument("request", "{\"name\":\"json-user\"}");
        GrpcSampler<HelloRequest, HelloReply> sampler = new GrpcSampler<>();
        JavaSamplerContext context = new JavaSamplerContext(args);

        sampler.setupTest(context);
        try {
            SampleResult result = sampler.runTest(context);

            assertThat(result.getSamplerData()).isEqualTo("{\"name\":\"json-user\"}");
        } finally {
            sampler.teardownTest(context);
        }
    }
}
