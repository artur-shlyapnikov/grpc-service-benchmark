GRPC_VERSION=v1.67.1
PROJECT_NAME=grpc-perf-lab
DOCKER_COMPOSE=docker compose
BUILD_DIR=$(PROJECT_NAME)/bin
DOCKER_PROJECT_PREFIX=grpc-perf-lab

# directory structure
CONFIG_DIR=config
GRAFANA_DIR=$(CONFIG_DIR)/grafana
PROMETHEUS_DIR=$(CONFIG_DIR)/prometheus
INFLUXDB_DIR=$(CONFIG_DIR)/influxdb
CHAOS_DIR=chaos-testing

# colors for better output
COLOR_RESET=\033[0m
COLOR_BLUE=\033[34m
COLOR_GREEN=\033[32m
COLOR_RED=\033[31m
COLOR_YELLOW=\033[33m

.PHONY: all init build test clean docker-up docker-down help install-tools setup validate-env lint check-monitoring run-chaos stop-chaos

all: help

setup:
	@echo "$(COLOR_BLUE)Setting up project directory structure...$(COLOR_RESET)"
	@mkdir -p $(GRAFANA_DIR)/provisioning/datasources
	@mkdir -p $(GRAFANA_DIR)/provisioning/dashboards
	@mkdir -p $(GRAFANA_DIR)/dashboards
	@mkdir -p $(PROMETHEUS_DIR)
	@mkdir -p $(INFLUXDB_DIR)
	@mkdir -p templates
	@echo "$(COLOR_GREEN)Directory structure created$(COLOR_RESET)"

	@# Create default configs if they don't exist
	@if [ ! -f templates/prometheus.yml ]; then \
		echo "$(COLOR_BLUE)Creating default Prometheus template...$(COLOR_RESET)"; \
		cp prometheus.yml templates/; \
	fi

	@if [ ! -f templates/datasources.yml ]; then \
		echo "$(COLOR_BLUE)Creating default Grafana datasource template...$(COLOR_RESET)"; \
		echo 'apiVersion: 1\n\ndatasources:\n  - name: Prometheus\n    type: prometheus\n    access: proxy\n    url: http://prometheus:9090\n    isDefault: true\n\n  - name: InfluxDB\n    type: influxdb\n    access: proxy\n    url: http://influxdb:8086\n    jsonData:\n      version: Flux\n      organization: performance-testing\n      defaultBucket: k6\n      tlsSkipVerify: true\n    secureJsonData:\n      token: my-super-secret-auth-token' > templates/datasources.yml; \
	fi

	@# Copy templates to config directories
	@echo "$(COLOR_BLUE)Copying configuration files...$(COLOR_RESET)"
	@cp templates/prometheus.yml $(PROMETHEUS_DIR)/
	@cp templates/datasources.yml $(GRAFANA_DIR)/provisioning/datasources/

	@echo "$(COLOR_GREEN)Setup complete!$(COLOR_RESET)"

validate-env:
	@echo "$(COLOR_BLUE)Validating environment...$(COLOR_RESET)"
	@if ! command -v docker >/dev/null 2>&1; then \
		echo "$(COLOR_RED)Error: docker is not installed$(COLOR_RESET)" >&2; \
		exit 1; \
	fi
	@if ! command -v docker compose >/dev/null 2>&1; then \
		echo "$(COLOR_RED)Error: docker compose is not installed$(COLOR_RESET)" >&2; \
		exit 1; \
	fi
	@echo "$(COLOR_GREEN)Environment validation passed$(COLOR_RESET)"

install-tools: validate-env
	@echo "$(COLOR_BLUE)Installing required tools...$(COLOR_RESET)"
	@./scripts/install-tools.sh

init: install-tools setup
	@echo "$(COLOR_BLUE)Initializing project...$(COLOR_RESET)"
	@if [ -d "$(PROJECT_NAME)" ]; then \
		echo "$(COLOR_YELLOW)Project directory already exists$(COLOR_RESET)"; \
	else \
		git clone -b $(GRPC_VERSION) --depth 1 https://github.com/grpc/grpc-go && \
		mkdir -p $(PROJECT_NAME) && \
		cp -r grpc-go/examples/helloworld/* $(PROJECT_NAME)/ && \
		rm -rf grpc-go && \
		cd $(PROJECT_NAME) && \
		go mod init $(PROJECT_NAME) && \
		go mod tidy && \
		echo "$(COLOR_GREEN)Project initialized successfully$(COLOR_RESET)"; \
	fi

build:
	@echo "$(COLOR_BLUE)Building project...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && \
	echo "Building server..." && \
	go build -v -o bin/server ./greeter_server && \
	echo "Building client..." && \
	go build -v -o bin/client ./greeter_client && \
	echo "$(COLOR_GREEN)Build complete$(COLOR_RESET)"

test:
	@echo "$(COLOR_BLUE)Running tests...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && go test -v ./...

docker-up: validate-env setup
	@echo "$(COLOR_BLUE)Starting Docker services...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) up -d

docker-down:
	@echo "$(COLOR_BLUE)Stopping Docker services...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) down

clean: ## clean only build artifacts
	@echo "$(COLOR_BLUE)Cleaning build artifacts...$(COLOR_RESET)"
	@rm -rf $(BUILD_DIR)
	@find . -name '*.test' -delete
	@echo "$(COLOR_GREEN)Build artifacts cleaned$(COLOR_RESET)"

docker-clean: ## clean only docker resources related to this project
	@echo "$(COLOR_BLUE)Cleaning docker resources...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) down --rmi local --volumes --remove-orphans
	@docker images --filter "reference=$(DOCKER_PROJECT_PREFIX)*" -q | xargs -r docker rmi
	@echo "$(COLOR_GREEN)Docker resources cleaned$(COLOR_RESET)"

config-clean: ## clean configuration directories
	@echo "$(COLOR_BLUE)Cleaning configuration directories...$(COLOR_RESET)"
	@rm -rf $(CONFIG_DIR)
	@echo "$(COLOR_GREEN)Configuration directories cleaned$(COLOR_RESET)"

deep-clean: clean docker-clean config-clean ## clean everything including build artifacts and docker resources
	@echo "$(COLOR_GREEN)Deep clean complete$(COLOR_RESET)"

proto:
	@echo "$(COLOR_BLUE)Generating proto files...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && \
	protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		helloworld/helloworld.proto

check-monitoring:
	@echo "$(COLOR_BLUE)Checking monitoring setup...$(COLOR_RESET)"
	@chmod +x ./scripts/check-monitoring.sh
	@./scripts/check-monitoring.sh || (echo "$(COLOR_RED)Monitoring check failed. Run 'make docker-up' and try again$(COLOR_RESET)" && exit 1)

lint:
	@echo "$(COLOR_BLUE)Running linter...$(COLOR_RESET)"
	@golangci-lint run ./...

run-chaos: docker-up check-monitoring
	@echo "$(COLOR_YELLOW)WARNING: Chaos testing will introduce deliberate failures$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)Starting chaos testing...$(COLOR_RESET)"
	@if [ ! -x $(CHAOS_DIR)/chaos-toolkit.sh ]; then \
		echo "$(COLOR_RED)Error: $(CHAOS_DIR)/chaos-toolkit.sh not found or not executable$(COLOR_RESET)" >&2; \
		exit 1; \
	fi
	@if ! docker ps --format '{{.Names}}' | grep -q "^$(DOCKER_PROJECT_PREFIX)"; then \
		echo "$(COLOR_RED)Error: Project containers not running. Run 'make docker-up' first$(COLOR_RESET)" >&2; \
		exit 1; \
	fi
	@if [ ! -f /var/run/chaos-monkey.pid ]; then \
		sudo CONTAINER_NAME=$(DOCKER_PROJECT_PREFIX)_grpc-server_1 $(CHAOS_DIR)/chaos-toolkit.sh & \
		echo $$! | sudo tee /var/run/chaos-monkey.pid > /dev/null; \
		echo "$(COLOR_GREEN)Chaos testing started with PID $$(cat /var/run/chaos-monkey.pid)$(COLOR_RESET)"; \
	else \
		echo "$(COLOR_YELLOW)Chaos testing already running with PID $$(cat /var/run/chaos-monkey.pid)$(COLOR_RESET)"; \
	fi

stop-chaos:
	@echo "$(COLOR_BLUE)Stopping chaos testing...$(COLOR_RESET)"
	@if [ -f /var/run/chaos-monkey.pid ]; then \
		sudo kill $$(cat /var/run/chaos-monkey.pid) 2>/dev/null || true; \
		sudo rm -f /var/run/chaos-monkey.pid; \
		echo "$(COLOR_GREEN)Chaos testing stopped$(COLOR_RESET)"; \
	else \
		echo "$(COLOR_YELLOW)No running chaos testing found$(COLOR_RESET)"; \
	fi


help:
	@echo "Available targets:"
	@echo "  setup        - Create necessary directory structure and configs"
	@echo "  init         - Initialize project (clone gRPC example and setup)"
	@echo "  build        - Build the project"
	@echo "  test         - Run tests"
	@echo "  docker-up    - Start Docker services"
	@echo "  docker-down  - Stop Docker services"
	@echo "  clean        - Clean build artifacts only"
	@echo "  config-clean - Clean configuration directories"
	@echo "  docker-clean - Clean docker resources for this project"
	@echo "  deep-clean   - Clean everything (artifacts + docker + config)"
	@echo "  proto        - Generate proto files"
	@echo "  run-chaos    - Start chaos testing (requires sudo)"
	@echo "  stop-chaos   - Stop chaos testing (requires sudo)"