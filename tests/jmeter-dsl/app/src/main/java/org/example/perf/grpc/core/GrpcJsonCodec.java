package org.example.perf.grpc.core;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GrpcJsonCodec {
    private static final JsonFormat.Parser PARSER = JsonFormat.parser().ignoringUnknownFields();
    private static final JsonFormat.Printer PRINTER = JsonFormat.printer()
            .includingDefaultValueFields()
            .omittingInsignificantWhitespace();

    private GrpcJsonCodec() {
    }

    public static String toJson(Message message) throws InvalidProtocolBufferException {
        return PRINTER.print(message);
    }

    @SuppressWarnings("unchecked")
    public static <M extends Message> M parse(String json, Message.Builder builder) {
        try {
            PARSER.merge(json, builder);
            return (M) builder.build();
        } catch (Exception e) {
            log.error("Failed to parse request: {}", json, e);
            throw new RuntimeException("Failed to parse request", e);
        }
    }
}
