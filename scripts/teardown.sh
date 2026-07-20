#!/bin/bash

# Stop and remove ganache container
docker stop ganache 2>/dev/null && docker rm ganache 2>/dev/null

# Known Docker network name
NETWORK_NAME="ict3914-final-year-project_rabbit-network"

echo "Inspecting network '$NETWORK_NAME' for attached containers..."

# Get container names attached to the network
CONTAINERS=$(docker network inspect "$NETWORK_NAME" -f '{{range .Containers}}{{.Name}} {{end}}')

if [ -z "$CONTAINERS" ]; then
  echo "No containers attached to '$NETWORK_NAME'."
else
  echo "Removing attached containers: $CONTAINERS"
  for CONTAINER in $CONTAINERS; do
    echo "Removing container: $CONTAINER"
    docker rm -f "$CONTAINER" 2>/dev/null
  done
fi

# Remove the network
echo "Deleting network: $NETWORK_NAME"
docker network rm "$NETWORK_NAME"

echo "Network '$NETWORK_NAME' deleted successfully."
