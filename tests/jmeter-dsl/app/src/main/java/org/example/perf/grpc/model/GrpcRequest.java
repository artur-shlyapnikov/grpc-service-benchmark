package org.example.perf.grpc.model;

import com.google.protobuf.Message;
import lombok.Builder;
import lombok.Value;
import java.time.Duration;

@Value
@Builder(toBuilder = true)
public class GrpcRequest {
    String methodName;
    Message request;
    @Builder.Default
    Duration deadline = Duration.ofSeconds(1);
}