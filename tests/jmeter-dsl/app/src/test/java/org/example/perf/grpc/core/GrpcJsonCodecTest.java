package org.example.perf.grpc.core;

import io.grpc.examples.helloworld.HelloRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcJsonCodecTest {
    @Test
    void printsRequestAsCompactJson() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("test-user").build();

        assertThat(GrpcJsonCodec.toJson(request)).isEqualTo("{\"name\":\"test-user\"}");
    }

    @Test
    void parsesRequestIgnoringUnknownFields() {
        HelloRequest parsed = GrpcJsonCodec.parse(
                "{\"name\":\"test-user\",\"unknownField\":\"x\"}", HelloRequest.newBuilder());

        assertThat(parsed.getName()).isEqualTo("test-user");
    }

    @Test
    void printParseRoundTripPreservesMessage() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("round-trip").build();

        HelloRequest parsed = GrpcJsonCodec.parse(GrpcJsonCodec.toJson(request), HelloRequest.newBuilder());

        assertThat(parsed).isEqualTo(request);
    }
}
