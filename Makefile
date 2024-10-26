GRPC_VERSION=v1.67.1
PROJECT_NAME=grpc-perf-lab
DOCKER_COMPOSE=docker compose
BUILD_DIR=$(PROJECT_NAME)/bin
DOCKER_PROJECT_PREFIX=grpc-perf-lab

COLOR_RESET=\033[0m
COLOR_BLUE=\033[34m
COLOR_GREEN=\033[32m

.PHONY: all init build test clean docker-up docker-down help install-tools

all: help

install-tools:
	@echo "$(COLOR_BLUE)Installing required tools...$(COLOR_RESET)"
	@./scripts/install-tools.sh

init: install-tools
	@echo "$(COLOR_BLUE)Initializing project...$(COLOR_RESET)"
	@if [ -d "$(PROJECT_NAME)" ]; then \
		echo "Project directory already exists"; \
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

docker-up:
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

deep-clean: clean docker-clean ## clean everything including build artifacts and docker resources
	@echo "$(COLOR_GREEN)Deep clean complete$(COLOR_RESET)"

proto:
	@echo "$(COLOR_BLUE)Generating proto files...$(COLOR_RESET)"
	@cd $(PROJECT_NAME) && \
	protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		helloworld/helloworld.proto

.PHONY: lint
lint:
	@echo "$(COLOR_BLUE)Running linter...$(COLOR_RESET)"
	@golangci-lint run ./...

help:
	@echo "Available targets:"
	@echo "  init         - Initialize project (clone gRPC example and setup)"
	@echo "  build        - Build the project"
	@echo "  test         - Run tests"
	@echo "  docker-up    - Start Docker services"
	@echo "  docker-down  - Stop Docker services"
	@echo "  clean        - Clean build artifacts only"
	@echo "  docker-clean - Clean docker resources for this project"
	@echo "  deep-clean   - Clean everything (artifacts + docker)"
	@echo "  proto        - Generate proto files"
