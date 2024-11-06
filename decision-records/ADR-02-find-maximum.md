# ADR-02: Maximum load detection strategy

The ADR was simplified from a complex three-phase approach (quick approximation, binary search, verification) to a straightforward linear increment strategy with dual-threshold stopping conditions (error rate and latency degradation), making it more maintainable and easier to implement while potentially sacrificing some optimization precision.

## Context

Need a reliable method to determine maximum sustainable load for gRPC services with clear stopping criteria and stability verification.

## Decision

Implement incremental load testing with baseline comparison:

- Start at 1000 RPS, increase by 500 RPS steps
- 60-second test duration with 30-second ramp-up
- Stop when either:
  - Error rate exceeds 1%
  - P99 latency exceeds 2x baseline
- Capture baseline from first successful test
- Store metrics in InfluxDB for analysis

## Consequences

### Positive

- Simple, predictable load progression
- Clear stability criteria based on baseline
- Automated stopping conditions
- Comprehensive metrics collection

### Negative

- Fixed step size may miss optimal load point
- Linear search can be time-consuming

## Implementation Details

- TestConfig class for threshold configuration
- JMeter DSL with gRPC support
- InfluxDB integration for metrics storage
- Continuous error rate and latency monitoring
