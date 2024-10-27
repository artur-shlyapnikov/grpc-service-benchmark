package org.example.perf.grpc.core;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.grpc.Metadata;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.example.perf.grpc.model.GrpcRequest;
import org.example.perf.grpc.model.GrpcResponse;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GrpcSampler<REQ extends Message, RES extends Message> extends AbstractJavaSamplerClient {
    private ManagedChannel channel;
    private GrpcRequest request;
    private GrpcServiceCall<REQ, RES> serviceCall;
    private JsonFormat.Parser jsonParser;
    private JsonFormat.Printer jsonPrinter;

    @Override
    public void setupTest(JavaSamplerContext context) {
        try {
            jsonParser = JsonFormat.parser().ignoringUnknownFields();
            jsonPrinter = JsonFormat.printer()
                    .includingDefaultValueFields()
                    .omittingInsignificantWhitespace();

            String serviceCallClassName = context.getParameter("serviceCallClass");
            @SuppressWarnings("unchecked")
            Class<GrpcServiceCall<REQ, RES>> serviceCallClass =
                    (Class<GrpcServiceCall<REQ, RES>>) Class.forName(serviceCallClassName);
            this.serviceCall = serviceCallClass.getDeclaredConstructor().newInstance();

            // setup channel
            String host = context.getParameter("host", "localhost");
            int port = context.getIntParameter("port", 50051);
            boolean usePlaintext = Boolean.parseBoolean(context.getParameter("usePlaintext", "false"));
            Duration deadline = Duration.ofMillis(context.getLongParameter("deadlineMs", 1000));

            ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder
                    .forAddress(host, port)
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true);

            if (usePlaintext) {
                channelBuilder.usePlaintext();
            }

            channel = channelBuilder.build();

            String methodName = context.getParameter("methodName");
            String requestStr = context.getParameter("request");

            if (requestStr != null && !requestStr.isEmpty()) {
                REQ parsedRequest = parseRequest(requestStr, serviceCall.getRequestBuilder());
                request = GrpcRequest.builder()
                        .methodName(methodName)
                        .request(parsedRequest)
                        .deadline(deadline)
                        .build();

                log.info("Initialized gRPC request: method={}, request={}", methodName, requestStr);
            } else {
                throw new IllegalArgumentException("Request parameter is required");
            }
        } catch (Exception e) {
            log.error("Failed to setup gRPC sampler", e);
            throw new RuntimeException("Failed to setup gRPC sampler", e);
        }
    }

    @SuppressWarnings("unchecked")
    private REQ parseRequest(String requestStr, Message.Builder builder) throws Exception {
        try {
            jsonParser.merge(requestStr, builder);
            return (REQ) builder.build();
        } catch (Exception e) {
            log.error("Failed to parse request: {}", requestStr, e);
            throw new RuntimeException("Failed to parse request", e);
        }
    }

    @Override
    public SampleResult runTest(JavaSamplerContext context) {
        SampleResult result = new SampleResult();
        result.sampleStart();

        try {
            result.setSampleLabel("gRPC Request: " + request.getMethodName());
            GrpcResponse grpcResponse = executeGrpcCall();

            result.setSuccessful(grpcResponse.getStatus().isOk());
            result.setResponseCode(grpcResponse.getStatus().getCode().name());
            result.setResponseMessage(grpcResponse.getStatus().getDescription());

            if (grpcResponse.getResponse() != null) {
                String responseJson = jsonPrinter.print((Message) grpcResponse.getResponse());
                result.setResponseData(responseJson.getBytes());
            }

            result.setLatency(grpcResponse.getLatencyNanos() / 1_000_000); // convert to milliseconds
            result.setDataType("application/json");
            result.setSamplerData(jsonPrinter.print((Message) request.getRequest()));
            result.setRequestHeaders("gRPC method: " + request.getMethodName());

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