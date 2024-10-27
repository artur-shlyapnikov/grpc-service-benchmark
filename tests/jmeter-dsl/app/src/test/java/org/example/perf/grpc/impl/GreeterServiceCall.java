package org.example.perf.grpc.impl;

import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloReply;
import io.grpc.examples.helloworld.HelloRequest;
import org.example.perf.grpc.core.GrpcServiceCall;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class GreeterServiceCall implements GrpcServiceCall<HelloRequest, HelloReply> {

    @Override
    public HelloReply executeCall(HelloRequest request, ManagedChannel channel, Duration deadline) {
        GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        if (deadline != null) {
            stub = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS);
        }
        return stub.sayHello(request);
    }

    @Override
    public String getMethodName() {
        return "helloworld.Greeter/SayHello";
    }

    @Override
    public Message.Builder getRequestBuilder() {
        return HelloRequest.newBuilder();
    }
}