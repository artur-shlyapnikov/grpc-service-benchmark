# gRPC Performance Testing Lab

[![gRPC Version](https://img.shields.io/badge/gRPC-v1.67.1-blue)](https://grpc.io/)
[![JMeter DSL](https://img.shields.io/badge/JMeter-DSL-blue)](https://github.com/abstracta/jmeter-java-dsl)
[![k6](https://img.shields.io/badge/k6-0.54.0-blue)](https://k6.io/)

## Prerequisites

- Docker and Docker Compose
- Make
- Go
- Java 21 (for JMeter DSL tests)

## Quick start

1. Initialize the project:

```bash
make init
```

2. Start monitoring stack:

```bash
make docker/up
```

3. Run tests:

```bash
# Maximum load test with JMeter DSL
make test/max-load/jmeter

# Maximum load test with k6
make test/max-load/k6

# Run complete test cycle
make test/full-cycle
make test                # Shortcut to run all tests
```

## Makefile commands

### Setup

```bash
make init              # Initialize project
make build             # Build all components
make proto             # Generate protobuf files
```

### Testing

```bash
make test/max-load/jmeter     # Run max load test with JMeter
make test/max-load/k6         # Run max load test with k6
make test/reliability/jmeter  # Run reliability test with JMeter
make test/reliability/k6      # Run reliability test with k6
make test/full-cycle         # Run all tests
make test                    # Shortcut to run all tests
```

### Infrastructure

```bash
make docker/up        # Start all containers
make docker/down      # Stop all containers
make clean            # Clean build artifacts
make clean/docker     # Clean docker resources
make clean/deep       # Deep clean (artifacts + docker)
```

### Environment variables

The JMeter DSL tests read the target host and InfluxDB endpoint from the
environment or equivalent JVM system properties. By default everything runs
locally so these variables are optional.

| Variable | Default | Description |
|----------|---------|-------------|
| `TEST_HOST` (`-Dtest.host`)| `localhost` | Hostname of the gRPC service |
| `INFLUX_URL` (`-Dinflux.url`)| `http://localhost:8086/write?db=perf-tests` | URL for InfluxDB listener |


## Service endpoints

| Service | Port | URL |
|---------|------|-----|
| gRPC Server | 50052 (maps to 50051) | localhost:50052 |
| Prometheus | 9090 | <http://localhost:9090> |
| Grafana | 3000 | <http://localhost:3000> |
| InfluxDB | 8086 | <http://localhost:8086> |
| cAdvisor | 8080 | <http://localhost:8080> |
| Node Exporter | 9100 | <http://localhost:9100> |

Docker maps port `50052` on the host to `50051` in the container to avoid a
local port conflict. Adjust `docker-compose.yml` if you need a different
mapping.

## Resource Limits

| Service | Memory | CPU |
|---------|--------|-----|
| gRPC Server | 2G | 2 |
| Prometheus | 1G | - |
| InfluxDB | 2G | 1 |
| cAdvisor | 512M | - |
| Node Exporter | 128M | - |

## Monitoring

Default Grafana credentials:

- URL: <http://localhost:3000>
- Username: admin
- Password: admin

### Notes on monitoring

While the current monitoring setup provides core metrics for CPU, RAM, and gRPC-specific indicators, in a production environment, I would extend this with detailed network stack monitoring to better identify potential bottlenecks. For this demonstration, I focused on implementing the custom gRPC sampler for JMeter DSL and core performance metrics to meet the primary objectives while maintaining reasonable delivery timeframes.

## Project structure

```
.
├── config/                    # Configuration files
│   ├── grafana/              # Grafana dashboards
│   ├── prometheus/           # Prometheus config
├── grpc-perf-lab/            # gRPC server
├── tests/                    # Test files
│   ├── jmeter-dsl/          # JMeter DSL tests
│   └── k6/                  # k6 test scripts
└── scripts/                  # Utility scripts
```

## JVM options

Default JVM options for test execution:

```
-Xmx2g -Xms2g
-XX:MaxMetaspaceSize=512m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100
-XX:+ParallelRefProcEnabled
```
