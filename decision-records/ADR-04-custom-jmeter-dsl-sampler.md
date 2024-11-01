# ADR-05: Implementation of gRPC sampler for performance testing

## Context

Performance testing gRPC services requires specific protocol handling that isn't natively supported by JMeter.
Standard HTTP samplers cannot properly handle gRPC's binary protocol, streaming capabilities, and type safety requirements.

## Decision

I implemented a custom JMeter sampler specifically for gRPC, built on JMeter's JavaDSL with with the required functionality for the task protocol support and type safety through generics.

## Consequences

### Positive

- Native gRPC protocol support with proper binary handling
- Type-safe request/response handling through generics
- Performance metrics collection
- Protocol-specific error handling and reporting
- Dev-friendly DSL for test creation
- Proper resource management for gRPC channels
- Reusable components for different gRPC services

### Negative

- Additional maintenance overhead for custom code
- Steep learning curve

## References

- [JMeter JavaDSL documentation](https://abstracta.github.io/jmeter-java-dsl/guide/)
- [gRPC Java documentation](https://grpc.io/docs/languages/java/)
- Current implementation in `/test/java/org/example/perf/grpc/`
