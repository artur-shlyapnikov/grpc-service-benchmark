# gRPC performance testing lab

This repository contains a local test lab for a small Go gRPC service and two
load-test implementations. The service implements
`helloworld.Greeter/SayHello`. JMeter tests use a custom Java DSL sampler. k6
scripts live in `tests/k6`.

This is a lab for performance experiments, not a production service. The
Compose setup uses plaintext gRPC and local credentials from
`docker-compose.yml`. Test parameters are fixed in the source, and this
checkout does not contain raw benchmark output.

## Service under test

The protocol definition is in
[`grpc-perf-lab/helloworld/helloworld.proto`](grpc-perf-lab/helloworld/helloworld.proto).
The Go server implements `helloworld.Greeter/SayHello` and returns `Hello `
followed by the request name.

The server accepts these flags:

```text
-port          gRPC port, default 50051
-metrics-port  HTTP metrics port, default 2112
```

The server enables gRPC reflection and serves Prometheus metrics at
`/metrics`. The standalone Go client defaults to `localhost:50051`. The
Compose mapping uses host port `50052`, so a client running on the host must
use `localhost:50052`.

## Prerequisites

- Docker with the Compose plugin. `make validate-env` checks for `docker` and
  `docker compose`.
- GNU Make.
- Go. The installer rejects versions older than 1.21. The service module
  declares Go 1.22.7 and `toolchain go1.23.2` in
  [`grpc-perf-lab/go.mod`](grpc-perf-lab/go.mod), so use a compatible Go
  installation.
- JDK 21 for the Gradle and JMeter tests. The Gradle wrapper uses Gradle
  8.10.2.
- Network access for Docker image pulls and Gradle or Maven dependency
  downloads.

`make install-tools` installs the Go protobuf generators, `golangci-lint`,
`grpcurl`, and k6. The versions for the Go tools are in [`tools.mk`](tools.mk).
On Linux, the installer downloads k6 with `curl` and uses `sudo` to place it in
`/usr/local/bin`. On macOS, it uses Homebrew.

## Quick start

The repository already contains the Go service. A normal checkout does not
need `make init`.

Start the Compose stack:

```bash
make docker/up
docker compose ps
```

Run the standalone Go client against the mapped host port:

```bash
(cd grpc-perf-lab && go run ./greeter_client -addr localhost:50052 -name World)
```

The client logs `Greeting: Hello World` when the call succeeds. Stop the
stack when you finish:

```bash
make docker/down
```

The gRPC server also registers reflection. If `grpcurl` is installed, you can
list the reflected services with:

```bash
grpcurl -plaintext localhost:50052 list
```

## Compose services

`docker-compose.yml` defines the following services.

| Service | Host endpoint | Configuration |
| --- | --- | --- |
| gRPC server | `localhost:50052` | Maps to port `50051` in the container. Metrics use `localhost:2112`. |
| Prometheus | <http://localhost:9090> | Scrapes `grpc-server:2112`, `cadvisor:8080`, `node-exporter:9100`, and `k6:5656`. |
| Grafana | <http://localhost:3000> | Uses `admin` / `admin` and loads dashboards from `config/grafana/dashboards`. |
| InfluxDB | <http://localhost:8086> | Initializes the `performance-testing` organization and `perf-tests` bucket. |
| cAdvisor | <http://localhost:8080> | Exposes container metrics. |
| Node Exporter | <http://localhost:9100> | Exposes selected CPU, memory, load average, and network metrics. |
| k6 | No host port | Runs on the `monitoring` network and mounts `tests/k6` at `/scripts`. |

Prometheus scrapes the gRPC server every 15 seconds. It scrapes cAdvisor and
Node Exporter every 5 seconds. Grafana provisions Prometheus and InfluxDB data
sources and includes the gRPC and JMeter dashboards.

The Compose file contains these local InfluxDB values:

```text
Username: admin
Password: admin123
Organization: performance-testing
Bucket: perf-tests
Token: my-super-secret-auth-token
```

Do not reuse these values outside a local test environment.

The configured container limits are 2 CPUs and 2 GB for the gRPC server, 1
CPU and 2 GB for Prometheus, 1 CPU and 1 GB for Grafana, 2 CPUs and 4 GB for
InfluxDB, 0.5 CPU and 512 MB for cAdvisor, and 128 MB for Node Exporter.

## Run the JMeter scenarios

The JMeter project is under `tests/jmeter-dsl`. Both scenarios call
`helloworld.Greeter/SayHello` with the request name `World`, use plaintext
gRPC, and send results to the configured InfluxDB listener.

Start the Compose stack before either JMeter target. The JMeter targets do not
start Docker services themselves.

Run the maximum-load scenario:

```bash
make test/max-load/jmeter
```

The implementation starts with a target value of 1000, adds 500 after each
step, ramps for 30 seconds, and holds each step for 60 seconds. It stops when
the measured error rate exceeds 1% or the measured P99 latency exceeds twice
the first successful run's P99 latency. The code labels the target value as
RPS, but it creates a regular JMeter `threadGroup`. Treat
`stats.overall().samples().perSecond()` as the measured throughput.

Run the reliability scenario:

```bash
make test/reliability/jmeter
```

This scenario uses an RPS thread group, ramps to 4200 over 5 minutes, and
holds that target for 30 minutes. It calculates a maximum of 25,200 threads.
The final evaluation requires an error rate of at most 1%, P99 latency of at
most 900 ms, throughput variance of at most 20%, and at least six stable
measurement windows. A stable window must stay between 80% and 120% of the
target and meet the error and latency limits.

The reliability class creates six 5-minute entries after the test finishes,
but its `calculateMetrics` method uses the overall test statistics for every
entry. These entries are not independent time-window measurements.

### JMeter configuration

Both Java scenarios read the target host and InfluxDB URL in this order:

1. JVM system property
2. Environment variable
3. The default value

| Purpose | JVM property | Environment variable | Default |
| --- | --- | --- | --- |
| gRPC host | `-Dtest.host` | `TEST_HOST` | `localhost` |
| InfluxDB listener URL | `-Dinflux.url` | `INFLUX_URL` | `http://localhost:8086/write?db=perf-tests` |

The JMeter scenarios use port `50052` in the source. The Makefile forwards
`GRADLE_TEST_OPTS` to Gradle, so either form can be set explicitly:

```bash
TEST_HOST=127.0.0.1 make test/max-load/jmeter
GRADLE_TEST_OPTS="-Dtest.host=127.0.0.1 -Dinflux.url=http://127.0.0.1:8086/write?db=perf-tests" make test/max-load/jmeter
```

The fixed InfluxDB listener token is defined in the JMeter test sources and
matches the local Compose token. The tests do not expose a command-line
option for changing the gRPC port or enabling TLS.

## Run the k6 scenarios

The Makefile targets are:

```bash
make test/max-load/k6
make test/reliability/k6
```

Each target first runs `make build` and `make docker/up`. It then runs the
corresponding script in a temporary k6 container.

The maximum-load script loads `helloworld.proto`, invokes
`helloworld.Greeter/SayHello`, starts at 1000, uses 1000-unit approximation
steps and 500-unit refinement steps, and checks a 60-second measurement plus
30-second stability measurements. Its thresholds include P99 latency below
500 ms, an error rate below 1%, and measured throughput at least 95% of the
target.

The reliability script targets 20,302, ramps for 5 minutes, and holds the
target for 30 minutes. It evaluates an error rate below 1%, P99 latency below
500 ms, throughput variance at most 10%, and six stable windows. Its stable
window throughput range is 90% to 110% of the target.

The current k6 scripts do not call `client.connect()` and do not contain a
target address. They load `definitions/helloworld.proto`, but the repository
does not include that file under `tests/k6`, which is the only directory that
the Compose service mounts at `/scripts`. The scripts also use `grpc.StatusOK`
without declaring a `grpc` binding. The Makefile targets do not pass an
address, TLS options, or the `K6_OPTS` variable to k6. The repository therefore
does not provide a verified k6 connection setup. Review this before relying on
either k6 target.

`make test/full-cycle` runs both JMeter targets and both k6 targets.
`make test` is an alias for the full cycle. The current k6 limitations apply to
both aggregate targets.

## Build, test, and development commands

Build the Go binaries:

```bash
make build/go
```

This writes `grpc-perf-lab/bin/server` and `grpc-perf-lab/bin/client`.

Build the Java test project without running its tests:

```bash
make build/java
```

Build both projects:

```bash
make build
```

Regenerate the Go protobuf files from
`grpc-perf-lab/helloworld/helloworld.proto`:

```bash
make proto
```

Run the configured Go linter command:

```bash
make lint
```

Run the regular Gradle test task without the tagged load, performance, and
reliability tests:

```bash
(cd tests/jmeter-dsl && ./gradlew test)
```

The performance test targets run their tagged tests through these Gradle
tasks:

```text
runLoadTest
runReliabilityTest
```

`make init` is a bootstrap target for a checkout without
`grpc-perf-lab`. It first installs tools, then clones the gRPC Go repository at
`v1.67.1` and copies its Hello World example into `grpc-perf-lab`.

### JFR profiling

The profiling targets add a 180-second Java Flight Recorder recording:

```bash
make test/max-load/jmeter/profile
make test/reliability/jmeter/profile
make convert-jfr
```

Recordings are written under `tests/jmeter-dsl/jfr`. `make convert-jfr` writes
profile and summary text files next to each recording. The Makefile resolves
`JAVA_HOME` with macOS's `/usr/libexec/java_home`, so check that path before
using these targets on another operating system.

### Cleanup

```bash
make clean
make docker/down
make clean/docker
make clean/deep
```

`make clean` removes Go build output and files ending in `.test`.
`make clean/docker` removes the Compose stack, its volumes, local images, and
orphans selected by the Makefile. `make clean/deep` runs both cleanup steps.
Use these targets only when you no longer need the generated artifacts or
local monitoring data.

## Project layout

```text
config/
  grafana/                 Grafana provisioning and dashboards
  prometheus/              Prometheus scrape configuration
grpc-perf-lab/              Go gRPC service, client, and proto files
tests/jmeter-dsl/           Java DSL sampler and JMeter scenarios
tests/k6/                   k6 scripts and helpers
decision-records/           Design decision records
report/                     Historical test report
scripts/                    Tool installation script
```

## Known limitations

- The server and JMeter client use plaintext gRPC. The Compose file stores
  Grafana and InfluxDB credentials in plain text.
- The gRPC port is fixed at `50052` in both JMeter scenarios. The Go server
  itself defaults to `50051`; Compose maps host `50052` to that container port.
- The Dockerfile healthcheck and the Compose healthcheck call `nc`, but the
  Dockerfile does not install a netcat package. Check the actual container
  health state in your Docker environment.
- The Compose k6 image is `grafana/k6` without a tag. `tools.mk` declares
  `K6_VERSION=v0.54.0` for the installer, but Compose does not pin that image
  to the declared version.
- Prometheus scrapes `k6:5656`, but the Compose k6 service does not publish
  that port or configure a k6 metrics endpoint.
- The maximum-load k6 script declares a 5% stability threshold, while
  `calculateStabilityWindow` rejects throughput spread above 10%.
- `report/README.md` contains historical figures from an external test
  environment. This checkout has no raw metrics or JFR files to reproduce
  those figures. The constants in the current scenario sources are the
  authoritative test settings for this repository.
