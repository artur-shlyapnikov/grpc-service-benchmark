package org.example.perf.grpc.sampler;

import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.example.perf.grpc.model.GrpcRequest;

import java.time.Duration;

// to make it easier to create gRPC samplers
@Slf4j
public class GrpcSamplerBuilder {
    private String host = "localhost";
    private int port = 50051;
    private boolean usePlaintext = false;
    private Duration deadline = Duration.ofSeconds(1);
    private Message request;
    private String methodName;

    public GrpcSamplerBuilder host(String host) {
        this.host = host;
        return this;
    }

    public GrpcSamplerBuilder port(int port) {
        this.port = port;
        return this;
    }

    public GrpcSamplerBuilder usePlaintext() {
        this.usePlaintext = true;
        return this;
    }

    public GrpcSamplerBuilder deadline(Duration deadline) {
        this.deadline = deadline;
        return this;
    }

    public GrpcSamplerBuilder request(Message request) {
        this.request = request;
        return this;
    }

    public GrpcSamplerBuilder methodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    public GrpcSampler build() {
        // create channel
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(host, port);

        if (usePlaintext) {
            channelBuilder.usePlaintext();
        }

        ManagedChannel channel = channelBuilder.build();

        // create request
        GrpcRequest grpcRequest = GrpcRequest.builder()
                .methodName(methodName)
                .request(request)
                .deadline(deadline)
                .build();

        return GrpcSampler.builder()
                .channel(channel)
                .request(grpcRequest)
                .build();
    }

    public static GrpcSamplerBuilder newBuilder() {
        return new GrpcSamplerBuilder();
    }
}