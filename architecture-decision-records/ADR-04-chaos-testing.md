# ADR-04: Custom bash-based chaos testing implementation

## Context

To accomplish item 5 of the assignment, “Conduct reliability testing”, I needed a reliable way to perform chaos testing on our containerized gRPC services.

Options included:

- Using existing tools (Chaos Mesh, Litmus)
- Building custom solution in Go/Python
- Creating lightweight bash-based toolkit

## Decision

I chose to implement a custom bash-based chaos toolkit because:

1. Zero external dependencies beyond standard Linux tools (tc, docker, iptables)
2. Direct control over container resources and network conditions
3. Simple to audit, modify, and debug
4. Minimal resource overhead

## Consequences

### Positive

- No additional software installation required
- Transparent operation (easy to see what's happening)
- Lightweight and fast execution
- Easy to extend with new chaos patterns
- Direct access to container management
- Plays well with existing monitoring setup

### Negative

- Requires root privileges
- Less sophisticated than specialized chaos tools
- Manual implementation of safety measures
