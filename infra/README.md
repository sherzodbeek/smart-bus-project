# Infrastructure

Local infrastructure assets for SmartBus live here.

- `docker-compose.yml`: starts PostgreSQL and Kafka for local development
- `postgres/init/`: initialization scripts for local databases

Primary local runtime command:

```sh
docker compose -f infra/docker-compose.yml up -d
```

PostgreSQL is published on host port `5433` to avoid conflicts with a local PostgreSQL instance that may already be using `5432`.

If Docker reports that the Kafka image cannot be found, use the current `infra/docker-compose.yml`. The stack now uses the official Apache image `apache/kafka:latest` with a documented single-node KRaft configuration instead of the older Bitnami reference, which no longer resolves reliably on Docker Hub.
