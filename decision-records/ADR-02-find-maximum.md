# ADR-02: Maximum load detection

## Context

Need a reliable method to determine maximum sustainable load for gRPC services that:

- Avoids false maximums from temporary throughput variations
- Efficiently searches through large load ranges
- Verifies stability of discovered maximum

## Decision

Implement a three-phase maximum load detection algorithm:

1. Quick Approximation Phase
   - Start at 1000 RPS
   - Use 2000 RPS increments
   - Stop when throughput growth falls below 110%

2. Binary Search Phase
   - Search window: ±2000 RPS around approximation
   - Use 200 RPS precision steps
   - Require 95% efficiency ratio (actual/target throughput)

3. Verification Phase
   - Require 3 stable measurements
   - Maximum 5% throughput variance allowed
   - Test duration: 5 minutes for initial, 2 minutes for stability

## Consequences

### Positive

- Reliable detection of true maximum capacity
- Efficient testing time through adaptive step sizes
- Built-in stability verification
- Clear sustainable load recommendation (80% of maximum)

### Negative

- Longer test duration due to multiple verification runs
- Resource overhead from repeated measurements
- May need parameter tuning for different service types

## Implementation Details

- Error rate tracking for each measurement
- P99 latency monitoring
- InfluxDB metrics storage
- Automated stability window calculations

--****-

```mermaid
flowchart TB
    Start([Start]) --> Init[Initialize at 1000 RPS]

    %% Phase 1: Quick Approximation
    subgraph Phase1[Phase 1: Quick Approximation]
        Init --> MeasureA[Measure Load<br>5min duration]
        MeasureA --> StableA{Stable?<br>±5% variance}
        StableA -->|No| ReturnPrev[Return Previous<br>Stable Rate]
        StableA -->|Yes| CheckGrowth{Throughput<br>Growth > 110%?}
        CheckGrowth -->|Yes| Increment[Increment by<br>2000 RPS]
        Increment --> MeasureA
        CheckGrowth -->|No| ApproxFound[Approximate<br>Maximum Found]
    end

    %% Phase 2: Binary Search
    subgraph Phase2[Phase 2: Binary Search]
        ApproxFound --> SetRange[Set Search Range<br>±2000 RPS]
        SetRange --> BinaryStart[Calculate Mid Point]
        BinaryStart --> MeasureB[Measure Load]
        MeasureB --> StableB{Stable and<br>Efficient?}
        StableB -->|Yes| AdjustLeft[Move Left Bound<br>to Mid]
        StableB -->|No| AdjustRight[Move Right Bound<br>to Mid]
        AdjustLeft --> CheckRange{Range > 200 RPS?}
        AdjustRight --> CheckRange
        CheckRange -->|Yes| BinaryStart
        CheckRange -->|No| PreciseFound[Precise<br>Maximum Found]
    end

    %% Phase 3: Verification
    subgraph Phase3[Phase 3: Verification]
        PreciseFound --> Verify[Run 3x<br>Stability Tests]
        Verify --> FinalCheck{All Tests<br>Stable?}
        FinalCheck -->|Yes| Calculate[Calculate 80%<br>Sustainable Rate]
        FinalCheck -->|No| Adjust[Reduce Rate<br>by 200 RPS]
        Adjust --> Verify
        Calculate --> End([End])
    end
```
