package org.example.perf.grpc.sampler;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloReply;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcRequest;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GrpcSampler extends AbstractJavaSamplerClient {
    private ManagedChannel channel;
    private GrpcRequest request;

    // Required no-args constructor
    public GrpcSampler() {
        super();
    }

    @Override
    public void setupTest(JavaSamplerContext context) {
        // Get configuration from context
        String host = context.getParameter("host", "localhost");
        int port = context.getIntParameter("port", 50051);
        boolean usePlaintext = Boolean.parseBoolean(context.getParameter("usePlaintext", "false"));
        String methodName = context.getParameter("methodName");
        String requestStr = context.getParameter("request");

        // Build channel
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(host, port);

        if (usePlaintext) {
            channelBuilder.usePlaintext();
        }

        channel = channelBuilder.build();

        // Parse request from string
        HelloRequest helloRequest = HelloRequest.newBuilder()
                .setName(requestStr)
                .build();

        // Build request object
        request = GrpcRequest.builder()
                .methodName(methodName)
                .request(helloRequest)
                .deadline(Duration.ofSeconds(1))
                .build();

        log.info("Set up gRPC sampler with host={}, port={}, method={}", host, port, methodName);
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.sampleStart();

        try {
            result.setSampleLabel("gRPC Request: " + request.getMethodName());

            long startTime = System.nanoTime();
            HelloReply response = executeGrpcCall();
            long endTime = System.nanoTime();

            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setResponseData(response.toString().getBytes());
            result.setLatency(endTime - startTime);
            result.setDataType("grpc");
            result.setSamplerData(request.toString());
            result.setRequestHeaders("gRPC method: " + request.getMethodName());

        } catch (Exception e) {
            result.setSuccessful(false);
            result.setResponseCode("ERROR");
            result.setResponseMessage(e.getMessage());
            log.error("Error executing gRPC call", e);
        } finally {
            result.sampleEnd();
        }

        return result;
    }

    private HelloReply executeGrpcCall() {
        GreeterGrpc.GreeterBlockingStub blockingStub = GreeterGrpc.newBlockingStub(channel);

        if (request.getDeadline() != null) {
            blockingStub = blockingStub.withDeadlineAfter(
                    request.getDeadline().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }

        return blockingStub.sayHello((HelloRequest) request.getRequest());
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("Successfully shut down gRPC channel");
            } catch (InterruptedException e) {
                log.error("Error shutting down gRPC channel", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}