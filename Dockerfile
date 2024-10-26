FROM golang:1.21-alpine AS builder

# install required packages in a single layer
RUN apk add --no-cache git protobuf-dev && \
    go install google.golang.org/protobuf/cmd/protoc-gen-go@latest && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .

# generate gRPC code and build in a single layer
RUN protoc --go_out=. --go_opt=paths=source_relative \
    --go-grpc_out=. --go-grpc_opt=paths=source_relative \
    helloworld/helloworld.proto && \
    CGO_ENABLED=0 GOOS=linux go build -o /go/bin/server greeter_server/main.go

# final stage
FROM alpine:3.18

# add non-root user
RUN adduser -D appuser
USER appuser

COPY --from=builder /go/bin/server /server

# add health check
HEALTHCHECK --interval=30s --timeout=3s \
    CMD nc -z localhost 50051 || exit 1

EXPOSE 50051

CMD ["/server"]