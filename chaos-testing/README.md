# Chaos testing toolkit

A deterministic chaos testing toolkit for containerized services.
Required to fulfill item #5 of the assignment: reliability testing.
Injects controlled chaos into the system to test reliability and resilience.
Inspired heavily by [netflix/chaosmonkey](https://github.com/Netflix/chaosmonkey).

## Features

- Network issues injection (latency, packet loss)
- Resource limitations (CPU, memory)
- Container disruptions (pause, restart)
- Deterministic chaos using seeds
- Metrics collection and logging
- Safe cleanup on exit

## Prerequisites

- Root access
- Docker
- tc (traffic control)
- iptables
- jq

## Quick start

```bash
# run with default settings
sudo ./chaos-toolkit.sh

# run with custom seed
SEED=12345 sudo ./chaos-toolkit.sh

# dry run mode
DRY_RUN=true sudo ./chaos-toolkit.sh
```

## Configuration

Main parameters in `chaos-config.env`:

```bash
CONTAINER_NAME="grpc-service"    # Target container
PROB_NETWORK_ISSUES=0.1         # Network issues probability
PROB_RESOURCE_LIMIT=0.05        # Resource limitation probability
PROB_CONTAINER_ACTIONS=0.02     # Container disruption probability
```

## Monitoring

- Logs: `/var/log/chaos-toolkit/chaos-toolkit.log`
- Metrics: `/var/log/chaos-toolkit/metrics.json`
- Status: `sudo kill -USR1 $(cat /var/run/chaos-toolkit.pid)`

## Safe usage

1. Always run in test environment first
2. Start with low probabilities
3. Monitor the metrics
4. Keep seed values for reproducing issues
