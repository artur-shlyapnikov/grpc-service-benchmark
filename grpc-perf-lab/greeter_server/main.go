package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"time"

	grpc_prometheus "github.com/grpc-ecosystem/go-grpc-prometheus"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"google.golang.org/grpc"
	pb "google.golang.org/grpc/examples/helloworld/helloworld"
	"google.golang.org/grpc/reflection"
)

var (
	port        = flag.Int("port", 50051, "The server port")
	metricsPort = flag.Int("metrics-port", 2112, "The metrics port")

	// application-specific metrics
	requestsProcessed = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "grpc_server_requests_processed_total",
			Help: "The total number of processed gRPC requests",
		},
		[]string{"method"},
	)

	requestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "grpc_server_request_duration_seconds",
			Help:    "Request duration in seconds",
			Buckets: []float64{.001, .005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10},
		},
		[]string{"method"},
	)
)

type server struct {
	pb.UnimplementedGreeterServer
}

func (s *server) SayHello(ctx context.Context, in *pb.HelloRequest) (*pb.HelloReply, error) {
	start := time.Now()

	reply := &pb.HelloReply{Message: "Hello " + in.GetName()}

	duration := time.Since(start).Seconds()
	requestsProcessed.WithLabelValues("SayHello").Inc()
	requestDuration.WithLabelValues("SayHello").Observe(duration)

	return reply, nil
}

func main() {
	flag.Parse()

	grpc_prometheus.EnableHandlingTimeHistogram()

	s := grpc.NewServer(
		grpc.UnaryInterceptor(grpc_prometheus.UnaryServerInterceptor),
		grpc.StreamInterceptor(grpc_prometheus.StreamServerInterceptor),
	)

	pb.RegisterGreeterServer(s, &server{})
	reflection.Register(s)
	grpc_prometheus.Register(s)

	go func() {
		mux := http.NewServeMux()
		mux.Handle("/metrics", promhttp.Handler())

		log.Printf("Starting metrics server on :%d", *metricsPort)
		if err := http.ListenAndServe(fmt.Sprintf(":%d", *metricsPort), mux); err != nil {
			log.Fatalf("Failed to start metrics server: %v", err)
		}
	}()

	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	log.Printf("server listening at %v", lis.Addr())
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
