package org.example.perf.grpc.core;

import com.google.protobuf.Message;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.JavaSampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;
import us.abstracta.jmeter.javadsl.core.samplers.BaseSampler;

public class DslGrpcSampler<REQ extends Message, RES extends Message>
        extends BaseSampler<DslGrpcSampler<REQ, RES>> {

    private String host = "localhost";
    private int port = 50052;
    private boolean usePlaintext = false;
    private REQ request;
    private final GrpcServiceCall<REQ, RES> serviceCall;

    public DslGrpcSampler(String name, GrpcServiceCall<REQ, RES> serviceCall) {
        super(name != null ? name : "gRPC Request", TestBeanGUI.class);
        this.serviceCall = serviceCall;
    }

    public static <REQ extends Message, RES extends Message> DslGrpcSampler<REQ, RES> grpcSampler(
            GrpcServiceCall<REQ, RES> serviceCall) {
        return new DslGrpcSampler<>(null, serviceCall);
    }

    public DslGrpcSampler<REQ, RES> host(String host) {
        this.host = host;
        return this;
    }

    public DslGrpcSampler<REQ, RES> port(int port) {
        this.port = port;
        return this;
    }

    public DslGrpcSampler<REQ, RES> usePlaintext() {
        this.usePlaintext = true;
        return this;
    }

    public DslGrpcSampler<REQ, RES> request(REQ request) {
        this.request = request;
        return this;
    }

    @Override
    protected TestElement buildTestElement() {
        JavaSampler sampler = new JavaSampler();
        sampler.setClassname(GrpcSampler.class.getName());

        Arguments arguments = new Arguments();
        arguments.addArgument("host", host);
        arguments.addArgument("port", String.valueOf(port));
        arguments.addArgument("usePlaintext", String.valueOf(usePlaintext));
        arguments.addArgument("methodName", serviceCall.getMethodName());
        arguments.addArgument("serviceCallClass", serviceCall.getClass().getName());

        if (request != null) {
            arguments.addArgument("requestRef", GrpcSampler.shareRequest(request));
        }

        sampler.setArguments(arguments);
        return sampler;
    }
}
