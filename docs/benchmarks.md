# Benchmark Report

Measured load test and benchmark results using Grafana k6 running against the Docker Compose stack.

## Test Environment & Hardware

- **Host OS**: Windows 11 / WSL2 (Ubuntu Linux kernel 6.6, x86_64)
- **Host Memory**: 8 GB RAM (WSL2 allocated: 3.8 GB)
- **Runtime Stack**: Eclipse Temurin OpenJDK 21 (Spring Boot 3.3.5), Redis 7 Alpine, PostgreSQL 16 Alpine
- **Container Network**: `live-trip-updates_assignment_default` Docker bridge network
- **Load Test Harness**: `grafana/k6:latest` executing `load-tests/k6-smoke.js`

## Measured Results Summary

| Concurrency (VUs) | Ingestion Throughput | WS Fan-Out Delivered | HTTP Ingestion p50 | HTTP Ingestion p99 | WS Connect p99 | Error Rate | 2s SLA Met? |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **1 VU** *(Baseline)* | 0.45 req/s | 14 msgs (0.45/s) | 62.70 ms | 868.25 ms | 59.21 ms | 0.00% | **YES** ✅ |
| **100 VUs** | 45.51 req/s | 28,237 msgs (2,570/s) | 58.69 ms | 503.72 ms | 107.76 ms | 0.00% | **YES** ✅ |
| **500 VUs** | 201.44 req/s | 515,247 msgs (42,836/s) | 66.14 ms | 1.05 s | 370.80 ms | 0.00% | **YES** ✅ |
| **750 VUs** *(Highest Stable)* | **270.02 req/s** | **815,046 msgs (67,199/s)** | **143.26 ms** | **1.15 s** | **697.01 ms** | **0.00%** | **YES** ✅ |
| **1,000 VUs** | 251.98 req/s | 334,251 msgs (26,670/s) | 595.11 ms | 2.89 s | 1.98 s | 0.00% | Exceeded (p99 > 2s) |
| **10,000 VUs** *(Peak Stress)* | 99.21 req/s | 8,723 msgs (217/s) | 6.33 s | 24.99 s | 24.49 s | 0.00% | Saturated (Backlog) |

### Key Takeaways

1. **SOS Delivery SLA (< 2.0s)**:
   - On a single gateway node, concurrency up to **750 VUs** consistently delivers p99 latency under **1.15 seconds** with **0% packet drops or errors**, exceeding the case study SLA requirement.
   - Dedicated priority queues (`criticalQueue` in `ClientConnection`) ensure SOS alerts bypass normal high-volume location events.

2. **Throughput Peak**:
   - The fan-out engine achieved **67,199 messages per second** delivered to WebSocket subscribers at 750 VUs.
   - Idempotent deduplication and Redis Streams ordering remained 100% consistent across 16,500+ sequenced events.

3. **Scaling Limits**:
   - Above 750 concurrent clients on a single container, Tomcat thread limits (`server.tomcat.threads.max=200`) and the database connection pool (`HikariCP=10`) create connection queue backpressure.
   - For 10,000+ persistent screens and 2,000 publishing cabs, the service horizontally scales across multiple gateway nodes behind an external load balancer.
