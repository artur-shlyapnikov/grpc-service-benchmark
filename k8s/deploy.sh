#!/bin/bash

# Set error handling
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Helper functions
log() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
    exit 1
}

wait_for_pod() {
    local namespace=$1
    local label=$2
    local timeout=$3

    log "Waiting for pod with label $label in namespace $namespace..."
    kubectl wait --for=condition=ready pod -l $label -n $namespace --timeout=${timeout}s || error "Pod with label $label failed to start"
}

check_namespace() {
    if ! kubectl get namespace monitoring >/dev/null 2>&1; then
        log "Creating monitoring namespace..."
        kubectl create -f monitoring-namespace.yaml
    else
        warn "Monitoring namespace already exists"
    fi
}

# Main deployment function
deploy_monitoring_stack() {
    log "Starting deployment of monitoring stack..."

    # Create namespace first
    check_namespace

    # Deploy RBAC configurations
    log "Deploying RBAC configurations..."
    kubectl apply -f metrics-server-rbac.yaml

    # Deploy core monitoring components
    log "Deploying core monitoring components..."
    kubectl apply -f prometheus-configmap.yaml
    kubectl apply -f prometheus-deployment.yaml
    kubectl apply -f prometheus-config.yaml

    # Deploy metrics server
    log "Deploying metrics server..."
    kubectl apply -f metrics-server.yaml

    # Deploy backing services
    log "Deploying InfluxDB..."
    kubectl apply -f influxdb-deployment.yaml
    kubectl apply -f influxdb-service.yaml

    # Deploy Grafana
    log "Deploying Grafana..."
    kubectl apply -f grafana-configmap.yaml
    kubectl apply -f grafana-datasources.yaml
    kubectl apply -f grafana-deployment.yaml
    kubectl apply -f grafana-service.yaml


    # Deploy gRPC server
    log "Deploying gRPC server..."
    kubectl apply -f grpc-server-deployment.yaml
    kubectl apply -f grpc-server-service.yaml

    # Deploy ingress configurations
    log "Deploying ingress configurations..."
    kubectl apply -f monitoring-ingress.yaml
    kubectl apply -f k3d-ingress.yaml
    kubectl apply -f traefik-middleware.yaml

    kubectl apply -f network-policy.yaml

    # Deploy service configurations
    log "Deploying service configurations..."
    kubectl apply -f monitoring-services.yaml
    kubectl apply -f service-ports.yaml

    # Wait for critical components
    wait_for_pod "monitoring" "app=prometheus" 120
    wait_for_pod "monitoring" "app=grafana" 120
    wait_for_pod "monitoring" "app=influxdb" 120

    log "Deployment completed successfully!"

    # Print access information
    echo -e "\n${GREEN}=== Access Information ===${NC}"
    echo "Grafana: http://localhost:3000"
    echo "Prometheus: http://localhost:9090"
    echo "InfluxDB: http://localhost:8086"
}

# Execute deployment
deploy_monitoring_stack