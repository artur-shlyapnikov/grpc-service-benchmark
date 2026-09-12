package org.example.perf.grpc.core;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public final class GrpcChannelFactory {
    private GrpcChannelFactory() {
    }

    public static ManagedChannel create(String host, int port, boolean usePlaintext) {
        ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                .forAddress(host, port)
                .keepAliveTime(120, TimeUnit.SECONDS)
                .keepAliveTimeout(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .maxInboundMetadataSize(16 * 1024)
                .maxInboundMessageSize(16 * 1024 * 1024)
                .idleTimeout(300, TimeUnit.SECONDS)
                .enableRetry()
                .maxRetryAttempts(1);

        if (usePlaintext) {
            channelBuilder.usePlaintext();
        }

        return channelBuilder.build();
    }

    public static void shutdown(ManagedChannel channel) {
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
