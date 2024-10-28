# ADR-03: Reliability testing strategy

## Context

Need to verify service reliability under adverse conditions using chaos engineering principles.
Using [chaos-toolkit](ADR/ADR-04-chaos-testing.md) for deterministic fault injection.

## Decision

Implement three-phase chaos testing strategy:

1. Basic reliability (4 hours per iteration):
   - Run with default probabilities
   - Three iterations with different seeds
   - Monitor recovery between chaos events

2. Focused chaos sessions:

   ```bash
   # Configuration sets:
   Set 1: Network-heavy
   - PROB_NETWORK_ISSUES=0.3
   - DURATION=2h

   Set 2: Resource-heavy
   - PROB_RESOURCE_LIMIT=0.15
   - DURATION=2h

   Set 3: Mixed chaos
   - All probabilities=0.1
   - DURATION=2h
   ```

3. Success Criteria:
   - Service self-recovers after each chaos event
   - Error rate returns to baseline within 30s
   - No manual intervention required
   - All metrics are captured in `/var/log/chaos-toolkit/metrics.json`

## Consequences

Positive:

- Reproducible chaos testing with seeds
- Total duration ~12 hours (manageable)
- Automated metrics collection
- Controlled and reversible chaos

Negative:

- Requires root access
- Limited to container-level chaos
- May need environment-specific tuning
