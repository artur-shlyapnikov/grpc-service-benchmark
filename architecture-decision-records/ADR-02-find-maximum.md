# ADR-02: Maximum load testing approach

## Context

I need a systematic and reproducible approach to determine the maximum load our containerized gRPC service can handle reliably.

## Decision

Implement a step-loading approach with specific stopping criteria:

1. Start at 100 RPC/sec
2. Double load until first saturation signs
3. Switch to 20% increments after first saturation signs
4. Stop when hitting any of:
   - Error rate > 0.1%
   - p99 latency 10x from baseline
   - Throughput plateau

Validate findings with:

- 10-min test at 90% of maximum
- 5-min test at 100%
- 3-min test at 110%

## Consequences

Positive:

- Repeatable process for finding service limits
- Clear stopping criteria
- Built-in validation phase

Negative:

- Time-consuming process
