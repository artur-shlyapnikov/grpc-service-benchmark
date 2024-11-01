# ADR-03: Reliability testing strategy

## Context

Need to define reliability testing approach for a basic gRPC hello world service. The service needs to demonstrate stable performance under sustained load over time.

## Decision

Implement a two-phase reliability testing strategy focusing on sustained load and basic monitoring:

1. Preparation Phase:
   - Conduct [maximum load test](decision-records/ADR-02-find-maximum.md) to establish performance baseline
   - Determine stable operating threshold (target: 80% of max throughput)

2. Reliability Test Execution:

   ```
   Duration: 2 hours
   Target Load: 80% of established maximum
   Pattern: Constant load
   ```

3. Success criteria:
   - Error rate stays below 0.1%
   - Response latency (p95) remains within 20% of initial baseline
   - No memory leaks observed
   - CPU utilization remains stable
   - No service restarts occur

4. Metrics to collect:

   ```
   Performance Metrics:
   - Throughput (RPS)
   - Response time (p50, p95, p99)
   - Error rate

   Resource Metrics:
   - CPU usage
   - Memory usage
   - Goroutine count
   - GC statistics
   ```

## Implementation details

1. Test Scripts:

   ```
   JMeter DSL:
   - Constant throughput timer
   - 2-hour duration

   k6 (comparison):
   - Equivalent constant load scenario
   - Same duration and metrics
   ```

2. Monitoring:

   ```
   Prometheus queries:
   - rate(grpc_server_handled_total[1m])
   - histogram_quantile(0.95, rate(grpc_server_handling_seconds_bucket[1m]))
   - process_cpu_seconds_total
   - go_memstats_alloc_bytes
   ```

3. Reporting:
   - Generate time-series graphs for all metrics
   - Calculate statistical stability indicators
   - Compare JMeter vs k6 results
   - Document any anomalies or patterns

## Consequences

### Positive

- Simple, reproducible test scenario
- Clear success criteria
- Comprehensive metrics collection
- Reasonable test duration
- Comparable results between tools

### Negative

- May not catch all production-like scenarios
- Limited to basic service behavior
- No complex failure modes tested

## Notes

- Test duration (2 hours) allows for observation of memory patterns and potential resource leaks
- Comparative testing between JMeter and k6 provides tool validation
