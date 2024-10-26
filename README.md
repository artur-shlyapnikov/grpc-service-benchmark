# gRPC Performance Testing Lab

[![Go Version](https://img.shields.io/badge/Go-1.22.7-blue.svg)](https://go.dev/)
[![gRPC Version](https://img.shields.io/badge/gRPC-v1.67.1-blue.svg)](https://grpc.io/)
[![Docker Required](https://img.shields.io/badge/Docker-Required-blue.svg)](https://www.docker.com/)

## Requirements

| Component | Version |
|-----------|---------|
| Go | ≥1.22.7 |
| Docker | Latest |
| Docker Compose | Latest |
| protoc | Latest |
| make | Latest |

## Quick start

1. Install tools:

```bash
make install-tools
```

2. Initialize project:

```bash
make init
make build
```

3. Start services:

```bash
make docker-up
```

4. Verify setup:

```bash
make check-monitoring
```

## Commands

### Setup

```bash
# create project structure
make setup

# initialize project
make init

# generate protocol buffers
make proto

# build binaries
make build
```

### Testing

```bash
# check monitoring stack
make check-monitoring
```

### Infrastructure

```bash
# start all services
make docker-up

# stop all services
make docker-down
```

### Cleanup

```bash
# clean build artifacts
make clean

# clean docker resources
make docker-clean

# clean config directories
make config-clean

# clean everything
make deep-clean
```

## Service endpoints

| Service | Port | URL | Purpose |
|---------|------|-----|---------|
| gRPC Server | 50052 | localhost:50052 | API endpoint |
| Metrics | 2112 | localhost:2112/metrics | Prometheus metrics |
| Grafana | 3000 | localhost:3000 | Metrics dashboard |
| Prometheus | 9090 | localhost:9090 | Metrics storage |
| InfluxDB | 8086 | localhost:8086 | k6 Results storage |
| cAdvisor | 8080 | localhost:8080 | Container metrics |

## Resource limits

| Service | Memory | CPU |
|---------|--------|-----|
| gRPC Server | 2GB | 2 cores |
| Prometheus | 1GB | - |
| InfluxDB | 1GB | 1 core |
| cAdvisor | 512MB | - |
| Node Exporter | 128MB | - |

## Monitoring

1. Access Grafana:
   - URL: <http://localhost:3000>
   - Username: admin
   - Password: admin

2. View metrics:
   - Prometheus: <http://localhost:9090>
   - Direct metrics: <http://localhost:2112/metrics>

## Troubleshooting

1. Service fails to start:

```bash
# check logs
docker compose logs grpc-server

# verify ports
netstat -tulpn | grep 50052
```

2. Monitoring not working:

```bash
# validate setup
make check-monitoring

# check prometheus targets
curl localhost:9090/api/v1/targets
```

3. Resource limits:

```bash
# view current usage
docker stats
```
