package org.example.perf.grpc.sampler;

import io.grpc.ManagedChannel;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloRequest;
import io.grpc.examples.helloworld.HelloReply;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcRequest;

import java.util.concurrent.TimeUnit;

@Slf4j
@Builder
public class GrpcSampler extends AbstractJavaSamplerClient {
    private final ManagedChannel channel;
    private final GrpcRequest request;

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.sampleStart();

        try {
            long startTime = System.nanoTime();

            HelloReply response = executeGrpcCall();

            long endTime = System.nanoTime();

            // store request data
            result.setSamplerData(request.toString());
            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setResponseData(response.toString().getBytes());
            result.setLatency(endTime - startTime);

            // add GRPC-specific data
            result.setRequestHeaders("gRPC method: " + request.getMethodName());
            result.setDataType("grpc");

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
        // create stub
        GreeterGrpc.GreeterBlockingStub blockingStub = GreeterGrpc.newBlockingStub(channel);

        // set deadline if specified
        if (request.getDeadline() != null) {
            blockingStub = blockingStub.withDeadlineAfter(
                    request.getDeadline().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        }

        // cast protobuf request to specific type
        HelloRequest helloRequest = (HelloRequest) request.getRequest();

        // make call
        return blockingStub.sayHello(helloRequest);
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Error shutting down gRPC channel", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}