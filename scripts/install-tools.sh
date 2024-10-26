#!/usr/bin/env bash
set -euo pipefail

source tools.mk

if [ -z "${GOPATH:-}" ]; then
    export GOPATH="$HOME/go"
    echo "GOPATH not set, using default: $GOPATH"
fi

if [ -z "${GOBIN:-}" ]; then
    export GOBIN="$GOPATH/bin"
    echo "GOBIN not set, using default: $GOBIN"
fi

if [[ ":$PATH:" != *":$GOBIN:"* ]]; then
    export PATH="$PATH:$GOBIN"
    echo "Added $GOBIN to PATH"
fi

echo "Environment:"
echo "GOPATH: $GOPATH"
echo "GOBIN: $GOBIN"
echo "PATH: $PATH"

echo "Checking Go version..."
GO_VERSION=$(go version | awk '{print $3}' | sed 's/go//')
if ! echo "$GO_VERSION $GO_MIN_VERSION" | awk '{exit !($1 >= $2)}'; then
    echo "Error: Go version must be >= $GO_MIN_VERSION (current: $GO_VERSION)" >&2
    exit 1
fi

echo "Checking protoc installation..."
if ! command -v protoc >/dev/null 2>&1; then
    echo "Error: protoc is not installed" >&2
    echo "Please install protoc first: https://grpc.io/docs/protoc-installation/" >&2
    exit 1
fi

echo "Installing development tools..."

echo "Installing Go tools..."
for tool in \
    "google.golang.org/protobuf/cmd/protoc-gen-go@${PROTOC_GEN_GO_VERSION}" \
    "google.golang.org/grpc/cmd/protoc-gen-go-grpc@${PROTOC_GEN_GO_GRPC_VERSION}" \
    "github.com/golangci/golangci-lint/cmd/golangci-lint@${GOLANGCI_LINT_VERSION}"
do
    echo "Installing $tool..."
    go install -v "$tool"

    binary=$(basename $(echo $tool | cut -d@ -f1))

    echo "Checking if $binary is in PATH..."
    if ! command -v "$binary" >/dev/null 2>&1; then
        echo "Warning: $binary not found in PATH"
        echo "Checking if it exists in GOBIN..."
        if [ -f "$GOBIN/$binary" ]; then
            echo "Found $binary in GOBIN but it's not accessible. Check permissions."
            ls -l "$GOBIN/$binary"
        else
            echo "Binary not found in GOBIN. Installation may have failed."
        fi
        exit 1
    else
        echo "$binary installed successfully at: $(which $binary)"
    fi
done

echo "Installing k6..."
if ! command -v k6 >/dev/null 2>&1; then
    case "$(uname -s)" in
        Linux*)
            curl -L https://github.com/grafana/k6/releases/download/${K6_VERSION}/k6-${K6_VERSION}-linux-amd64.tar.gz | tar xz
            sudo mv k6-${K6_VERSION}-linux-amd64/k6 /usr/local/bin/
            ;;
        Darwin*)
            brew install k6
            ;;
        *)
            echo "Unsupported OS for k6 installation" >&2
            exit 1
            ;;
    esac
fi

echo -e "\nInstalled versions:"
echo "Go: $(go version)"
echo "protoc: $(protoc --version)"
echo "protoc-gen-go: $(protoc-gen-go --version 2>/dev/null || echo 'Not found!')"
echo "protoc-gen-go-grpc: $(protoc-gen-go-grpc --version 2>/dev/null || echo 'Not found!')"
echo "golangci-lint: $(golangci-lint --version)"
echo "k6: $(k6 version)"

echo -e "\nInstallation directories:"
echo "protoc-gen-go: $(which protoc-gen-go 2>/dev/null || echo 'Not found!')"
echo "protoc-gen-go-grpc: $(which protoc-gen-go-grpc 2>/dev/null || echo 'Not found!')"
echo "golangci-lint: $(which golangci-lint 2>/dev/null || echo 'Not found!')"

echo -e "\nAll tools installed successfully!"