# Tatum Ethereum JSON-RPC Proxy

This is a Vert.x-based Java application that acts as a proxy for JSON-RPC 2.0 requests to an Ethereum node. It supports TLS termination, access logging, method invocation tracking.

## Requirements
- Java 20+
- Maven
- Docker (for containerization)
- TLS certificate and key files (e.g., cert.pem and key.pem; self-signed for testing) - default self-signed are included in ``tls`` directory.

## Configuration
The application loads configuration from `config.yaml` with overrides from environment variables for Docker compatibility. Example (default) `config.yaml`:
```yaml
tatumNode:
  host: "ethereum-mainnet.gateway.tatum.io"
  port: 443
proxy:
  port: 8443
  pathPrefix: "/eth"
  metricsPath: "/stats"
  healthPath: "/health"
  certPath: "tls/server-cert.pem"
  keyPath: "tls/server-key.pem"
  apiKey: "t-68da8d1b5f3c187b7b469acc-2d060a04766d440d82eef209"
```
#### Environment variables can override these settings (most interesting subset - see config.yaml):

TATUMNODE_HOST: Target Ethereum node host name  
TATUMNODE_PORT: Target Ethereum node port  
PROXY_PORT: Listening port  
PROXY_CERT_PATH: Path to TLS certificate file  
PROXY_KEY_PATH: Path to TLS private key file

## Building the Docker Image
Building process is multistage.

1. Ensure Maven is installed or use the Dockerfile which installs it.
2. Run:
  ```
  docker build -t tatum-proxy .
```
### Running the Application
Set environment variables and mount mount your TLS/SSL files as needed, or use configured defaults:

```
docker run -d -p 8443:8443 --name tatum-proxy tatum-proxy
```
Access the proxy at https://localhost:8443/eth (POST JSON-RPC requests).  
To view method counts: GET https://localhost:8443/stats  
To check the health of proxy: GET https://localhost:8443/health 

## Testing
Use the sample curl requests from task, replacing ``URL`` with ``https://localhost:8443/`` (accept self-signed cert, e.g., curl -k).  
For example:
```
curl -k -L 'https://localhost:8443/' -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":83}'
```
Check counts (after requests):
```
curl -k 'https://localhost:8443/stats'
```

## Design Decisions and Assumptions

- **Vert.x Version:** Used 5.0.4
- **Reverse Proxy:** Implemented using ``HttpProxy`` from ``vertx-http-proxy`` and ``ProxyHandler`` from ``vertx-web-proxy``. A ``ProxyInterceptor``, using ``transformingRequestBody`` handler, parses request bodies to count methods. Requests are forwarded to the target URL (supports HTTP/HTTPS).
- **TLS:** Requires PEM-formatted cert/key files. Backend connection uses SSL if target URL is HTTPS (with trustAll for simplicity).
- **Logging:** Basic access logs via Vert.x logger (request IP) before proxying including error logging.
- **Method Tracking:** Uses ``SharedData`` ``AsyncMap`` for counts. Increments are async and non-blocking (fire-and-forget). Exposed via ``/stats`` (configurable) endpoint.
- **HTTP Pooling:** Configured with max 10 connections per host and 30-second keep-alive timeout for better concurrency.
- **Configuration:** Loads from ``config.yaml`` with environment variable overrides for Docker.
- **Simplifications:** Request bodies are buffered in interceptor (suitable for small JSON-RPC requests). No authentication. Assumes valid JSON-RPC for method extraction. No clustering.
- **Performance:** Leverages Vert.x proxy for async proxying; interceptor, caching, and pooling add minimal overhead.
- **Structure for Readability:** Separated logic into classes: ConfigLoader for configuration, ProxyFactory for proxy configuration, CountService for body transformation and counting methods and /stats endpoint.

## Potential Improvements

- **Implement cache** - it should take in account:
  - batch requests
    - cache each request on its own
    - proxy only not cached requests
    - synchronize with responses
    - keep order of responses to correlate with requests
  - to not cache large responses like requests for logs
  - separate logic to be able to implement different caches (SharedData, Redis,...)
  - .... 
- Add rate limiting or auth.
- Add configurable timeouts, number of connections, etc..
- Stream large request bodies if needed (current buffering is fine for JSON-RPC).
- Persist counts to a database.
- More detailed logging.
- Proper SSL trust management for backend.
- Handle HTTP/2.
- Use more robust solution (in scalable, clustered environment), like Apache Kafka for metrics
