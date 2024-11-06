# gRPC Service performance test report

## Executive summary

### Key findings

- Maximum sustainable load: 4200 RPS
- Maximum peak load: 20,000 RPS
- CPU utilization at target load: ±157%
- Error rate: 0%

## Test environment

### Infrastructure

| Component | Specification |
|-----------|--------------|
| Memory | 2GB |
| Network | 1Gbps |
| Container limits | CPU: 2 cores, Memory: 2GB |

> [!NOTE]
> Testing moved to Hetzner Cloud due to container monitoring issues on macOS (Docker Desktop, Rancher Desktop, Lima).

Infrastructure:

**Load Generator:**

- CPX31: load-gen-ubuntu-8gb-fsn1-1
- Location: FSN1, EU-Central
- RAM: 8GB
- x86

**Docker host:**

- CCX33: ubuntu-32gb-fsn1-2
- Location: FSN1, EU-Central
- RAM: 32GB
- x86

> [!NOTE]
> Private network connects both instances in FSN1 datacenter with <2ms latency.

### Containerization

Please refer to the [ADR-01](decision-records/ADR-01-container-image.md) for more details.

### gRPC client configuration

| Setting | Value | Purpose |
|---------|-------|---------|
| keepAliveTime | 120s | Connection keep-alive interval |
| keepAliveTimeout | 30s | Keep-alive probe timeout |
| keepAliveWithoutCalls | false | Only send keep-alive during active RPCs |
| maxInboundMetadataSize | 16 KB | Maximum metadata size limit |
| maxInboundMessageSize | 16 MB | Maximum message size limit |
| idleTimeout | 300s | Connection idle timeout |
| maxRetryAttempts | 1 | Number of retry attempts |

> [!NOTE]
> Connection pooling and retry policies were configured to ensure reliable test execution while preventing resource exhaustion.

### Test tools

- JMeter DSL for load generation
- Prometheus for metrics collection
- Grafana for visualization
- InfluxDB for time-series data

> [!IMPORTANT]
> Due to last-minute InfluxDB issues, the raw metrics data is currently unavailable in the report, though all mentioned findings and conclusions remain accurate based on real-time observations during the testing.

## Test methodology

### Maximum Load Test

1. Initial load: 2000 RPS
2. Load increment: 2000 RPS
3. Fine-tuning: 1000 RPS
4. Stability check: 90s per step
5. Success criteria:
   - Throughput variance < 15%
   - Error rate = 0%
   - CPU utilization < 180%

For more details, see the [ADR-02](decision-records/ADR-02-find-maximum.md)

### Reliability test

1. Target load: 4200 RPS
2. Duration: 30 minutes
3. Ramp-up: 5 minutes
4. Success criteria:
   - Error rate < 1%
   - P99 latency <200ms
   - Throughput stability ±20%

## Maximum load test results

### Load characteristics

| Metric | Value |
|--------|--------|
| Maximum RPS | 20,000 |
| Sustainable RPS | 17,000 |
| CPU at max | 200% |
| Memory usage | 1.82GB |
| Error rate | 0% |

### Resource utilization

- CPU: Linear scaling until 200%
- Memory: Stable at 1.82GB
- Goroutines: Peak at 21,000
- GC impact: 0.07s duration

### System behavior

- Message processing ratio: 1.0
- File descriptors: Linear growth
- Heap usage: Controlled growth
- Active goroutines: Proportional scaling

## Reliability test results

### Performance Metrics

| Metric | Target | Actual |
|--------|---------|--------|
| RPS | 4200 | 4200 |
| Error rate | < 1% | 0% |
| P99 latency | < 200ms | 1ms |
| CPU usage | < 160% | 157% |

### Stability Metrics

- Throughput variance: ±5%
- CPU utilization: 157% ±7%
- Memory usage: Stable at 1.82GB
- GC cycles: Regular, minimal impact

## Analysis

### System bottlenecks

1. CPU capacity
   - Primary limitation
   - Linear scaling until 200%
   - Throttling starts at 180%

2. Memory management
   - Efficient usage
   - No leaks detected
   - Stable GC pattern

3. Network impact
   - No saturation observed
   - Connection handling efficient
   - No packet loss

### Scaling characteristics

1. Linear scaling until 17,000 RPS
2. Performance degradation starts at 20,000 RPS
3. Resource utilization proportional to load
4. Clean degradation pattern

## Recommendations

### Production limits

1. Set production limit to 4200 RPS
2. Implement auto-scaling at 3600 RPS
3. Set hard limit at 4500 RPS

### Monitoring thresholds

| Metric | Warning | Critical |
|--------|----------|-----------|
| CPU | 160% | 180% |
| Memory | 1.9GB | 2.0GB |
| Error rate | 0.5% | 1% |
| Latency p99 | 150ms | 200ms |

### Infrastructure recommendations

1. Consider CPU upgrade for higher throughput
2. Implement horizontal scaling
3. Add redundancy for reliability
4. Optimize container resource limits

## Appendix

### Load generator data

Based on the monitoring data from the load generator instance (as shown in the attached [JFRs](report/load-generator-utilization)), the test tooling exhibited stable and predictable resource utilization patterns throughout the test execution, with CPU spikes remaining within expected bounds and heap usage maintaining consistent levels.

### Test scripts

- [Maximum load test: Java/JMeter DSL](tests/jmeter-dsl/app/src/test/java/org/example/perf/grpc/MaximumLoadTest.java)
- [Reliability test: Java/JMeter DSL](tests/jmeter-dsl/app/src/test/java/org/example/perf/grpc/ReliabilityTest.java)

### Test Data

- Raw metrics stored in InfluxDB
- Grafana dashboards preserved:
  - [Max load](https://snapshots.raintank.io/dashboard/snapshot/iIQ3jkEpQULfoYRMGwM9TTetlcYq6QRk)
  - [Reliability](https://snapshots.raintank.io/dashboard/snapshot/aceWrrRx8eZWnyV5DSrMNwPdbdZrBBe2?orgId=0)
- Load generator utilization in JFR format (system and JVM-related information) [can be found here](report/load-generator-utilization).
