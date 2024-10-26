#!/usr/bin/env bash
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo "Checking monitoring health..."

check_grpc() {
    local port=$1
    local success=false

    # try grpcurl if available
    if command -v grpcurl >/dev/null 2>&1; then
        if grpcurl -plaintext -d '{"name": "healthcheck"}' localhost:$port helloworld.Greeter/SayHello > /dev/null 2>&1; then
            success=true
        fi
    fi

    # fallback to basic TCP connection test if grpcurl fails or isn't available
    if [ "$success" = false ]; then
        if nc -z localhost $port 2>/dev/null; then
            success=true
        fi
    fi

    return $([ "$success" = true ])
}

print_diagnostics() {
    echo -e "\nDiagnostic Information:"
    echo "1. Container Status:"
    docker compose ps grpc-server
    echo -e "\n2. Container Logs:"
    docker compose logs --tail=20 grpc-server
    echo -e "\n3. Port Status (50052):"
    nc -zv localhost 50052 2>&1 || true
    echo -e "\n4. Network Status:"
    docker compose exec grpc-server netstat -tulpn || true
    echo -e "\n5. Process Status:"
    docker compose exec grpc-server ps aux || true
}

# 1. check gRPC service
if ! check_grpc 50052; then
    echo -e "${RED}❌ gRPC service not responding${NC}"
    print_diagnostics
    exit 1
fi

# 2. check if metrics are exposed
if ! curl -s localhost:2112/metrics > /dev/null; then
    echo -e "${RED}❌ gRPC metrics not exposed${NC}"
    exit 1
fi

# 3. check if Prometheus is scraping
if ! curl -s localhost:9090/api/v1/query?query=up > /dev/null; then
    echo -e "${RED}❌ Prometheus not scraping metrics${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Monitoring is healthy - ready for testing!${NC}"