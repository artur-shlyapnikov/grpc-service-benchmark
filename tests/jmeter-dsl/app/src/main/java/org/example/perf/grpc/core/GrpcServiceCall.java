package org.example.perf.grpc.core;

import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import java.time.Duration;

public interface GrpcServiceCall<REQ extends Message, RES extends Message> {
    RES executeCall(REQ request, ManagedChannel channel, Duration deadline);
    String getMethodName();
    Message.Builder getRequestBuilder();
}