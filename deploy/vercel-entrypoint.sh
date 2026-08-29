#!/bin/sh
set -eu

public_port="${PORT:-8080}"
internal_port="${MCP_COMPASS_INTERNAL_PORT:-8081}"

/usr/bin/socat \
  "TCP-LISTEN:${public_port},reuseaddr,fork" \
  "TCP:127.0.0.1:${internal_port},forever,interval=0.1" &

exec /opt/java/openjdk/bin/java -jar /app/app.jar --server.port="${internal_port}"
