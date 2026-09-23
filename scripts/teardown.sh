#!/bin/bash

set -euo pipefail

NETWORK_NAME="ict3500-dissertation_rabbit-network"
POSTGRES_CONTAINER="postgres-db"

echo "Stopping application containers..."

docker stop producer-app 2>/dev/null || true
docker stop consumer-app 2>/dev/null || true

echo "Resetting experiment database tables..."

if docker ps --format '{{.Names}}' | grep -qx "$POSTGRES_CONTAINER"; then
  docker exec "$POSTGRES_CONTAINER" sh -c '
    psql \
      -v ON_ERROR_STOP=1 \
      -U "$POSTGRES_USER" \
      -d "$POSTGRES_DB" <<SQL
BEGIN;

TRUNCATE TABLE
    pending_orders_view,
    order_view,
    order_routing_view,
    token_entry
RESTART IDENTITY CASCADE;

COMMIT;
SQL
  '

  echo "Experiment tables reset successfully."
else
  echo "PostgreSQL container is not running; database tables were not reset."
fi

echo "Removing experiment containers..."

docker rm -f producer-app 2>/dev/null || true
docker rm -f consumer-app 2>/dev/null || true
docker rm -f axon-server 2>/dev/null || true
docker rm -f rabbit-broker 2>/dev/null || true
docker rm -f postgres-db 2>/dev/null || true
docker rm -f ganache 2>/dev/null || true

echo "Inspecting network '$NETWORK_NAME' for attached containers..."

if docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then

  CONTAINERS=$(
    docker network inspect \
      "$NETWORK_NAME" \
      -f '{{range .Containers}}{{.Name}} {{end}}'
  )

  if [ -z "$CONTAINERS" ]; then
    echo "No containers attached to '$NETWORK_NAME'."
  else
    echo "Removing attached containers: $CONTAINERS"

    for CONTAINER in $CONTAINERS; do
      echo "Removing container: $CONTAINER"
      docker rm -f "$CONTAINER" 2>/dev/null || true
    done
  fi

  echo "Deleting network: $NETWORK_NAME"
  docker network rm "$NETWORK_NAME" 2>/dev/null || true

else
  echo "Network '$NETWORK_NAME' does not exist."
fi

echo "Teardown completed successfully."