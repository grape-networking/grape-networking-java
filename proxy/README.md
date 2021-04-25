# grape-proxy
This runs on the cloud side of things. It listens for incoming requests from mobile VPN clients
using the grape vpn app.

## before implementing:
- what is the throughput limits of bidirectional grpc? It operates on http2 and TCP
- how does this compare to raw TCP? 
- how does this compare to raw UDP?

- pros:
  - easy to implement
  - don't need to mess with as much threading / syncronization
- cons:
  - tcp connection breakage makes solution brittle
  - can't use udp
  
## grpc implementation plan:
- ssl / tls between client and server:
  - https://grpc.io/docs/guides/auth/
  - https://bbengfort.github.io/2017/03/secure-grpc/
- gRPC bi-directional async stream originating at vpn client side
- initially one stream per client, eventually multiple streams (one-per interface)

## alternative implementation plan:
- design protobuf protocol without grpc
- 