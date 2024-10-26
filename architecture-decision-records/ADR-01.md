# ADR-01: Container image for gRPC Service

## Context

- Service requires gRPC protocol buffers compilation
- Build artifacts must be minimal

### In case of production usage

- Security posture must meet production standards

## Decision

Use multi-stage build with following specifications:

1. Base Images:

- Build: golang:1.21-alpine
- Runtime: alpine:3.18

2. Build Stage:

- Install protobuf tooling
- Compile protocol buffers
- Build with CGO_ENABLED=0
- Output static binary

3. Runtime Stage:

- Non-root user
- Health check via TCP port
- Single binary execution
- No development dependencies

## Consequences

### Positive

- Reduced attack surface
- Decreased image size
- Reproducible builds
- No runtime dependency on protobuf tools

### Negative

- Build time increases due to protobuf compilation
- Cannot debug with runtime tools
- Requires separate debug image for investigations

## Considered alternatives

1. Single stage build
2. Debian-based image
3. Pre-compiled protocol buffers

## Links

- [Docker Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Go - Protocol Buffers](https://protobuf.dev/getting-started/gotutorial/)
