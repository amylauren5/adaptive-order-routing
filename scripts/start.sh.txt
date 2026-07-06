#!/bin/sh
set -e

if ! docker network inspect ict3914-final-year-project_rabbit-network > /dev/null 2>&1; then
  echo "Creating Docker network..."
  docker network create ict3914-final-year-project_rabbit-network
else
  echo "Network already exists. Deleting it..."
  docker network rm ict3914-final-year-project_rabbit-network
  echo "Recreating Docker network..."
  docker network create ict3914-final-year-project_rabbit-network
fi


echo "Starting Ganache separately..."
docker run -d --name ganache --network ict3914-final-year-project_rabbit-network -p 8545:8545 \
  trufflesuite/ganache-cli \
  --gasLimit 12000000 --accounts 10 --defaultBalanceEther 100

echo "Running ganache-init script to extract contract info..."
docker run --rm  \
  -v "$PWD":/scripts -v /var/run/docker.sock:/var/run/docker.sock \
  -w /scripts docker:stable sh ./extract-ganache.sh

echo "Starting other services with docker-compose..."
docker compose up -d postgres rabbitmq 

sleep 5

docker compose up -d axon-server 

docker compose up -d producer-app consumer-app --build

echo "All services are up and running!"
