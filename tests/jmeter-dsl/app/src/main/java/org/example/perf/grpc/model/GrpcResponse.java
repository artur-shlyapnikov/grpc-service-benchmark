package org.example.perf.grpc.model;

import com.google.protobuf.Message;
import io.grpc.Status;
import io.grpc.Metadata;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class GrpcResponse {
    Message response;
    @Builder.Default
    Status status = Status.OK;
    @Builder.Default
    Metadata trailers = new Metadata();
    long latencyNanos;
}