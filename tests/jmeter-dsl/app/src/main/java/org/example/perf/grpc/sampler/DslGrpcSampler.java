package org.example.perf.grpc.sampler;

import us.abstracta.jmeter.javadsl.core.samplers.BaseSampler;
import org.apache.jmeter.protocol.java.sampler.JavaSampler;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testbeans.gui.TestBeanGUI;

// compatible with JMeter DSL
public class DslGrpcSampler extends BaseSampler<DslGrpcSampler> {

    private String host = "localhost";
    private int port = 50052;
    private boolean usePlaintext = false;
    private String methodName;
    private com.google.protobuf.Message request;

    public DslGrpcSampler(String name) {
        super(name != null ? name : "gRPC Request", TestBeanGUI.class); // we don't want to use GUI
    }

    public static DslGrpcSampler grpcSampler() {
        return new DslGrpcSampler(null);
    }

    public DslGrpcSampler host(String host) {
        this.host = host;
        return this;
    }

    public DslGrpcSampler port(int port) {
        this.port = port;
        return this;
    }

    public DslGrpcSampler usePlaintext() {
        this.usePlaintext = true;
        return this;
    }

    public DslGrpcSampler methodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    public DslGrpcSampler request(com.google.protobuf.Message request) {
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
        arguments.addArgument("methodName", methodName);
        if (request != null) {
            arguments.addArgument("request", request.toString());
        }

        sampler.setArguments(arguments);
        return sampler;
    }
}