#!/usr/bin/env bash
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "Checking monitoring health..."

# 1. check gRPC service
if ! grpcurl -plaintext -d '{"name": "healthcheck"}' localhost:50051 helloworld.Greeter/SayHello > /dev/null 2>&1; then
    echo -e "${RED}❌ gRPC service not responding${NC}"
    exit 1
fi

# 2. check if metrics are exposed
if ! curl -s localhost:2112/metrics | grep "grpc_server_requests_processed_total" > /dev/null; then
    echo -e "${RED}❌ gRPC metrics not exposed${NC}"
    exit 1
fi

# 3. check if Prometheus is scraping
if ! curl -s localhost:9090/api/v1/query?query=up | grep -q '"value":\[.*"1"'; then
    echo -e "${RED}❌ Prometheus not scraping metrics${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Monitoring is healthy - ready for testing!${NC}"