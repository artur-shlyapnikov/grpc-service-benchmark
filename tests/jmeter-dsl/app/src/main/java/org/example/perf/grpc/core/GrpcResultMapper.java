package org.example.perf.grpc.core;

import com.google.protobuf.Message;
import io.grpc.Metadata;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcResponse;

public final class GrpcResultMapper {
    private GrpcResultMapper() {
    }

    public static void map(SampleResult result, String methodName, Message request, GrpcResponse grpcResponse)
            throws Exception {
        result.setSampleLabel("gRPC Request: " + methodName);
        result.setSuccessful(grpcResponse.getStatus().isOk());
        result.setResponseCode(grpcResponse.getStatus().getCode().name());
        result.setResponseMessage(grpcResponse.getStatus().getDescription());

        if (grpcResponse.getResponse() != null) {
            String responseJson = GrpcJsonCodec.toJson(grpcResponse.getResponse());
            result.setResponseData(responseJson.getBytes());
        }

        result.setLatency(grpcResponse.getLatencyNanos() / 1_000_000);
        result.setDataType("application/json");
        result.setSamplerData(GrpcJsonCodec.toJson(request));
        result.setRequestHeaders("gRPC method: " + methodName);

        if (grpcResponse.getTrailers() != null && !grpcResponse.getTrailers().keys().isEmpty()) {
            StringBuilder trailers = new StringBuilder();
            for (String key : grpcResponse.getTrailers().keys()) {
                Metadata.Key<String> metadataKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                String value = grpcResponse.getTrailers().get(metadataKey);
                if (value != null) {
                    trailers.append(key).append(": ").append(value).append("\n");
                }
            }
            result.setResponseHeaders(trailers.toString());
        }
    }
}
