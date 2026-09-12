package org.example.perf.grpc.core;

import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcChannelFactoryTest {
    @Test
    void createsAndShutsDownChannel() {
        ManagedChannel channel = GrpcChannelFactory.create("localhost", 50051, true);

        try {
            assertThat(channel.isShutdown()).isFalse();
        } finally {
            GrpcChannelFactory.shutdown(channel);
        }

        assertThat(channel.isShutdown()).isTrue();
        GrpcChannelFactory.shutdown(channel);
    }

    @Test
    void shutdownToleratesNull() {
        GrpcChannelFactory.shutdown(null);
    }
}
