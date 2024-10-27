package org.example.perf.grpc.sampler;

import com.google.protobuf.Message;

import io.grpc.ManagedChannel;
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
            // measure time
            long startTime = System.nanoTime();

            // here we can call gRPC method
            Message response = executeGrpcCall();

            long endTime = System.nanoTime();

            result.setSuccessful(true);
            result.setResponseCodeOK();
            result.setResponseMessage("OK");
            result.setResponseData(response.toString().getBytes());
            result.setLatency(endTime - startTime);

        } catch (Exception e) {
            result.setSuccessful(false);
            result.setResponseCode("500");
            result.setResponseMessage(e.getMessage());
        } finally {
            result.sampleEnd();
        }

        return result;
    }

    private Message executeGrpcCall() {
        // TODO: реализовать вызов gRPC метода
        throw new UnsupportedOperationException("Not implemented yet");
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