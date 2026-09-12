package org.example.perf.grpc.core;

import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcRequest;
import org.example.perf.grpc.model.GrpcResponse;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class GrpcSampler<REQ extends Message, RES extends Message> extends AbstractJavaSamplerClient {
    private static final ConcurrentMap<String, Message> SHARED_REQUESTS = new ConcurrentHashMap<>();

    private ManagedChannel channel;
    private GrpcRequest request;
    private GrpcServiceCall<REQ, RES> serviceCall;

    public static String shareRequest(Message request) {
        String ref = UUID.randomUUID().toString();
        SHARED_REQUESTS.put(ref, request);
        return ref;
    }

    @Override
    public void setupTest(JavaSamplerContext context) {
        try {
            String serviceCallClassName = context.getParameter("serviceCallClass");
            @SuppressWarnings("unchecked")
            Class<GrpcServiceCall<REQ, RES>> serviceCallClass =
                    (Class<GrpcServiceCall<REQ, RES>>) Class.forName(serviceCallClassName);
            this.serviceCall = serviceCallClass.getDeclaredConstructor().newInstance();

            String host = context.getParameter("host", "localhost");
            int port = context.getIntParameter("port", 50051);
            boolean usePlaintext = Boolean.parseBoolean(context.getParameter("usePlaintext", "false"));
            Duration deadline = Duration.ofMillis(context.getLongParameter("deadlineMs", 1000));

            channel = GrpcChannelFactory.create(host, port, usePlaintext);

            String methodName = context.getParameter("methodName");
            REQ parsedRequest = resolveRequest(context, serviceCall.getRequestBuilder());
            request = GrpcRequest.builder()
                    .methodName(methodName)
                    .request(parsedRequest)
                    .deadline(deadline)
                    .build();

            log.info("Initialized gRPC request: method={}, request={}", methodName, parsedRequest);
        } catch (Exception e) {
            log.error("Failed to setup gRPC sampler", e);
            throw new RuntimeException("Failed to setup gRPC sampler", e);
        }
    }

    @SuppressWarnings("unchecked")
    private REQ resolveRequest(JavaSamplerContext context, Message.Builder builder) {
        String ref = context.getParameter("requestRef");
        if (ref != null && !ref.isEmpty()) {
            Message shared = SHARED_REQUESTS.get(ref);
            if (shared != null) {
                return (REQ) shared;
            }
        }

        String requestStr = context.getParameter("request");
        if (requestStr != null && !requestStr.isEmpty()) {
            return GrpcJsonCodec.parse(requestStr, builder);
        }

        throw new IllegalArgumentException("Request parameter is required");
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.sampleStart();

        try {
            GrpcResponse grpcResponse = executeGrpcCall();
            GrpcResultMapper.map(result, request.getMethodName(), request.getRequest(), grpcResponse);
        } catch (Exception e) {
            result.setSuccessful(false);
            result.setResponseCode("INTERNAL_ERROR");
            result.setResponseMessage(e.getMessage());
            log.error("Error executing gRPC call", e);
        } finally {
            result.sampleEnd();
        }

        return result;
    }

    private GrpcResponse executeGrpcCall() {
        long startTime = System.nanoTime();
        try {
            @SuppressWarnings("unchecked")
            RES response = serviceCall.executeCall(
                    (REQ) request.getRequest(),
                    channel,
                    request.getDeadline()
            );
            long endTime = System.nanoTime();

            return GrpcResponse.builder()
                    .response(response)
                    .status(Status.OK)
                    .latencyNanos(endTime - startTime)
                    .build();
        } catch (StatusRuntimeException e) {
            long endTime = System.nanoTime();
            return GrpcResponse.builder()
                    .status(e.getStatus())
                    .trailers(e.getTrailers())
                    .latencyNanos(endTime - startTime)
                    .build();
        }
    }

    @Override
    public void teardownTest(JavaSamplerContext context) {
        GrpcChannelFactory.shutdown(channel);
    }
}
