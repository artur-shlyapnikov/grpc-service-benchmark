package org.example.perf.grpc.core;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcResultMapperTest {
    @Test
    void mapsSuccessfulResponse() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("test-user").build();
        HelloReply reply = HelloReply.newBuilder().setMessage("Hello test-user").build();
        Metadata trailers = new Metadata();
        trailers.put(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER), "req-1");
        GrpcResponse response = GrpcResponse.builder()
                .response(reply)
                .status(Status.OK)
                .trailers(trailers)
                .latencyNanos(2_500_000)
                .build();
        SampleResult result = new SampleResult();

        GrpcResultMapper.map(result, "helloworld.Greeter/SayHello", request, response);

        assertThat(result.getSampleLabel()).isEqualTo("gRPC Request: helloworld.Greeter/SayHello");
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getResponseCode()).isEqualTo("OK");
        assertThat(new String(result.getResponseData())).isEqualTo("{\"message\":\"Hello test-user\"}");
        assertThat(result.getLatency()).isEqualTo(2);
        assertThat(result.getDataType()).isEqualTo("application/json");
        assertThat(result.getSamplerData()).isEqualTo("{\"name\":\"test-user\"}");
        assertThat(result.getRequestHeaders()).isEqualTo("gRPC method: helloworld.Greeter/SayHello");
        assertThat(result.getResponseHeaders()).contains("x-request-id: req-1");
    }

    @Test
    void mapsErrorStatusWithoutResponse() throws Exception {
        HelloRequest request = HelloRequest.newBuilder().setName("test-user").build();
        GrpcResponse response = GrpcResponse.builder()
                .status(Status.NOT_FOUND.withDescription("missing"))
                .latencyNanos(1_000_000)
                .build();
        SampleResult result = new SampleResult();

        GrpcResultMapper.map(result, "helloworld.Greeter/SayHello", request, response);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("NOT_FOUND");
        assertThat(result.getResponseMessage()).isEqualTo("missing");
        assertThat(new String(result.getResponseData())).isEmpty();
        assertThat(result.getSamplerData()).isEqualTo("{\"name\":\"test-user\"}");
    }
}
