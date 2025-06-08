GRPC_VERSION=v1.67.1
PROJECT_NAME=grpc-perf-lab
DOCKER_COMPOSE=docker compose
DOCKER_PROJECT_PREFIX ?= $(PROJECT_NAME)

# directory structure
CONFIG_DIR=config
GRAFANA_DIR=$(CONFIG_DIR)/grafana
PROMETHEUS_DIR=$(CONFIG_DIR)/prometheus
INFLUXDB_DIR=$(CONFIG_DIR)/influxdb
GRADLE_CMD=./gradlew
BUILD_DIR=$(PROJECT_NAME)/bin
JAVA_TEST_DIR=tests/jmeter-dsl

# colors for better output
COLOR_RESET=\033[0m
COLOR_BLUE=\033[34m
COLOR_GREEN=\033[32m
COLOR_RED=\033[31m
COLOR_YELLOW=\033[33m

# JVM Options for performance tests
DEFAULT_JAVA_OPTS=-Xmx2g -Xms2g \
    -XX:MaxMetaspaceSize=512m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=100 \
    -XX:+ParallelRefProcEnabled \
    -XX:ErrorFile=./hs_err_pid%p.log \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=./java_pid%p.hprof \
    -Xss1m
JAVA_OPTS ?= $(DEFAULT_JAVA_OPTS)
GRADLE_OPTS=-Dorg.gradle.parallel=true \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.configureondemand=true \
    -Dorg.gradle.jvmargs="$(DEFAULT_JAVA_OPTS)"

# Function to generate unique JFR filename with timestamp
JFR_FILENAME=$(JAVA_TEST_DIR)/jfr/$(1)-$(shell date +%Y%m%d_%H%M%S).jfr

JFR_OPTS=-XX:+FlightRecorder \
    -XX:StartFlightRecording=duration=180s,filename=$(1),settings=profile \
    -XX:FlightRecorderOptions=stackdepth=128
JAVA_HOME := $(shell /usr/libexec/java_home)

JFR_DIR=$(JAVA_TEST_DIR)/jfr

K6_OPTS=--out influxdb=http://localhost:8086/k6

# Create JFR directory during initialization
$(shell mkdir -p $(JFR_DIR))

.PHONY: all init build test clean docker-up docker-down help install-tools validate-env lint

all: help

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

init: install-tools
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

build/go:
	@echo "$(COLOR_BLUE)Building Go project...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && \
	echo "Building server..." && \
	go build -v -o bin/server ./greeter_server && \
	echo "Building client..." && \
	go build -v -o bin/client ./greeter_client && \
	echo "$(COLOR_GREEN)Go build complete$(COLOR_RESET)"

build/java:
	@echo "$(COLOR_BLUE)Building Java project...$(COLOR_RESET)"
	@cd tests/jmeter-dsl && \
	./gradlew build -x test && \
	echo "$(COLOR_GREEN)Java build complete$(COLOR_RESET)"

build: build/go build/java ## Build all projects
	@echo "$(COLOR_GREEN)All builds completed successfully$(COLOR_RESET)"

docker/up: validate-env
	@echo "$(COLOR_BLUE)Starting Docker services...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) up -d

docker/down:
	@echo "$(COLOR_BLUE)Stopping Docker services...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) down

clean: ## clean only build artifacts
	@echo "$(COLOR_BLUE)Cleaning build artifacts...$(COLOR_RESET)"
	@rm -rf $(BUILD_DIR)
	@find . -name '*.test' -delete
	@echo "$(COLOR_GREEN)Build artifacts cleaned$(COLOR_RESET)"

clean/docker: ## clean only docker resources related to this project
	@echo "$(COLOR_BLUE)Cleaning docker resources...$(COLOR_RESET)"
	@$(DOCKER_COMPOSE) down --rmi local --volumes --remove-orphans
	@docker images --filter "reference=$(DOCKER_PROJECT_PREFIX)*" -q | xargs -r docker rmi
	@echo "$(COLOR_GREEN)Docker resources cleaned$(COLOR_RESET)"

clean/deep: clean clean/docker
	@echo "$(COLOR_GREEN)Deep clean complete$(COLOR_RESET)"

proto:
	@echo "$(COLOR_BLUE)Generating proto files...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && \
	protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		helloworld/helloworld.proto

lint:
	@echo "$(COLOR_BLUE)Running linter...$(COLOR_RESET)"
	@golangci-lint run ./...

test/max-load/jmeter:
	@echo "$(COLOR_BLUE)Running maximum load tests...$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)Using JVM options: $(JAVA_OPTS)$(COLOR_RESET)"
	@cd $(JAVA_TEST_DIR) && \
	$(GRADLE_CMD) clean runLoadTest $(GRADLE_TEST_OPTS) --info || \
	(echo "$(COLOR_RED)Load test failed. Check logs for details$(COLOR_RESET)" && exit 1)
	@echo "$(COLOR_GREEN)Load tests completed successfully$(COLOR_RESET)"

test/max-load/k6: build docker/up
	docker compose run --rm k6 run /scripts/maximum-load.js

test/reliability/k6: build docker/up
	docker compose run --rm k6 run /scripts/reliability.js


test/reliability/jmeter:
	@echo "$(COLOR_BLUE)Running reliability tests...$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)Using JVM options: $(JAVA_OPTS)$(COLOR_RESET)"
	@cd $(JAVA_TEST_DIR) && \
	$(GRADLE_CMD) clean runReliabilityTest $(GRADLE_TEST_OPTS) --info || \
	(echo "$(COLOR_RED)Reliability test failed. Check logs for details$(COLOR_RESET)" && exit 1)
	@echo "$(COLOR_GREEN)Reliability tests completed successfully$(COLOR_RESET)"

test/max-load/jmeter/profile:
	@echo "$(COLOR_BLUE)Running maximum load tests with JFR profiling...$(COLOR_RESET)"
	$(eval JFR_OUTPUT := $(call JFR_FILENAME,max-load))
	@echo "$(COLOR_BLUE)Using JVM options: $(JAVA_OPTS) $(call JFR_OPTS,$(JFR_OUTPUT))$(COLOR_RESET)"
	@cd $(JAVA_TEST_DIR) && \
	JAVA_OPTS="$(JAVA_OPTS) $(call JFR_OPTS,$(JFR_OUTPUT))" $(GRADLE_CMD) clean runLoadTest $(GRADLE_TEST_OPTS) --info || \
	(echo "$(COLOR_RED)Load test failed. Check logs for details$(COLOR_RESET)" && exit 1)
	@echo "$(COLOR_GREEN)Load tests completed successfully$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)JFR recording saved to $(JFR_OUTPUT)$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)To analyze, open with JDK Mission Control or use 'jfr print jmeter-recording.jfr'$(COLOR_RESET)"

test/reliability/jmeter/profile:
	@echo "$(COLOR_BLUE)Running reliability tests with JFR profiling...$(COLOR_RESET)"
	$(eval JFR_OUTPUT := $(call JFR_FILENAME,reliability))
	@echo "$(COLOR_BLUE)Using JVM options: $(JAVA_OPTS) $(call JFR_OPTS,$(JFR_OUTPUT))$(COLOR_RESET)"
	@cd $(JAVA_TEST_DIR) && \
	JAVA_OPTS="$(JAVA_OPTS) $(call JFR_OPTS,$(JFR_OUTPUT))" $(GRADLE_CMD) clean runReliabilityTest $(GRADLE_TEST_OPTS) --info || \
	(echo "$(COLOR_RED)Reliability test failed. Check logs for details$(COLOR_RESET)" && exit 1)
	@echo "$(COLOR_GREEN)Reliability tests completed successfully$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)JFR recording saved to $(JFR_OUTPUT)$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)To analyze, open with JDK Mission Control or use 'jfr print jmeter-recording.jfr'$(COLOR_RESET)"


test/full-cycle: test/max-load/jmeter test/max-load/k6 test/reliability/jmeter test/reliability/k6 ## run all performance tests
	@echo "$(COLOR_GREEN)All performance tests completed successfully$(COLOR_RESET)"

convert-jfr: ## Convert JFR recording to text format
	@echo "$(COLOR_BLUE)Converting JFR to text formats...$(COLOR_RESET)"
	@if [ -d "$(JFR_DIR)" ] && [ "$$(ls -A $(JFR_DIR))" ]; then \
		cd $(JFR_DIR) && \
		for jfr in *.jfr; do \
		echo "$(COLOR_BLUE)Generating profile...$(COLOR_RESET)" && \
		$(JAVA_HOME)/bin/java -XX:+FlightRecorder \
			-XX:StartFlightRecording=disk=false \
			-XX:FlightRecorderOptions=stackdepth=128 \
			--add-exports jdk.jfr/jdk.jfr.internal.tool=ALL-UNNAMED \
			-cp $(JAVA_HOME)/lib/jfr.jar \
			jdk.jfr.internal.tool.Main print "$$jfr" > "$${jfr%.jfr}-profile.txt" && \
		echo "$(COLOR_GREEN)Profile saved to $(JFR_DIR)/$${jfr%.jfr}-profile.txt$(COLOR_RESET)" && \
		echo "$(COLOR_BLUE)Extracting important sections...$(COLOR_RESET)" && \
		echo "=== CPU Usage ===" > "$${jfr%.jfr}-summary.txt" && \
		grep -A 20 "CPU Usage" "$${jfr%.jfr}-profile.txt" >> "$${jfr%.jfr}-summary.txt" && \
		echo "\n=== Memory Usage ===" >> "$${jfr%.jfr}-summary.txt" && \
		grep -A 20 "Heap Summary" "$${jfr%.jfr}-profile.txt" >> "$${jfr%.jfr}-summary.txt" && \
		echo "\n=== GC Activity ===" >> "$${jfr%.jfr}-summary.txt" && \
		grep -A 20 "Garbage Collection" "$${jfr%.jfr}-profile.txt" >> "$${jfr%.jfr}-summary.txt" && \
		echo "$(COLOR_GREEN)Summary saved to $(JFR_DIR)/$${jfr%.jfr}-summary.txt$(COLOR_RESET)"; \
		done; \
	else \
		echo "$(COLOR_RED)No JFR recordings found in $(JFR_DIR)$(COLOR_RESET)"; \
	fi


help:
	@echo "Available targets:"
	@echo "  init         - Initialize project (clone gRPC example and setup)"
	@echo "  build        - Build the project"
	@echo "  test         - Run tests"
	@echo "  docker/up    - Start Docker services"
	@echo "  docker/down  - Stop Docker services"
	@echo "  clean        - Clean build artifacts only"
	@echo "  clean/docker - Clean docker resources for this project"
	@echo "  clean/deep   - Clean everything (artifacts + docker + config)"
	@echo "  proto        - Generate proto files"
	@echo "  test/max-load/jmeter     - Run maximum load tests with JMeter DSL"
	@echo "  test/max-load/k6 - Run maximum load tests with k6"
	@echo "  test-reliability - Run reliability tests with JMeter DSL"
	@echo "  test/full-cycle     - Run all performance tests"
