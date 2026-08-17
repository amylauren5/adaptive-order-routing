#!/bin/sh

set -eu

SCRIPT_DIR=$(
    CDPATH= cd -- "$(dirname -- "$0")" && pwd
)

PROJECT_DIR=$(
    CDPATH= cd -- "$SCRIPT_DIR/.." && pwd
)

COMPOSE_FILE="$PROJECT_DIR/docker-compose.yml"
ENV_FILE="$SCRIPT_DIR/.env"

NETWORK_NAME="ict3500-dissertation_rabbit-network"

GANACHE_CONTAINER="ganache"
POSTGRES_CONTAINER="postgres-db"
RABBITMQ_CONTAINER="rabbit-broker"
AXON_CONTAINER="axon-server"
CONSUMER_CONTAINER="consumer-app"
PRODUCER_CONTAINER="producer-app"

. "$SCRIPT_DIR/setup/common.sh"
. "$SCRIPT_DIR/setup/blockchain.sh"

# --------------------------------------------------
# Experiment arguments
# --------------------------------------------------

ROUTING_STRATEGY="${1:-training}"
WORKLOAD_RANDOM_SEED="${2:-1002}"
WORKLOAD_ARRIVAL_SCALE="${3:-2.0}"
WORKLOAD_BURST_ENABLED="${4:-false}"
WORKLOAD_BURST_MULTIPLIER="${5:-1.0}"
WORKLOAD_DURATION_SECONDS="${6:-30}"

validate_experiment_arguments

if [ "$ROUTING_STRATEGY" = "training" ]; then
    TRAINING_COLLECTION_ENABLED="true"
else
    TRAINING_COLLECTION_ENABLED="false"
fi

EVALUATION_COLLECTION_ENABLED="true"

EXPERIMENT_RUN_ID=$(
    build_experiment_run_id \
        "$ROUTING_STRATEGY" \
        "$WORKLOAD_ARRIVAL_SCALE" \
        "$WORKLOAD_RANDOM_SEED" \
        "$WORKLOAD_BURST_ENABLED" \
        "$WORKLOAD_BURST_MULTIPLIER" \
        "$WORKLOAD_DURATION_SECONDS"
)

export ROUTING_STRATEGY
export WORKLOAD_RANDOM_SEED
export WORKLOAD_ARRIVAL_SCALE
export WORKLOAD_BURST_ENABLED
export WORKLOAD_BURST_MULTIPLIER
export WORKLOAD_DURATION_SECONDS
export TRAINING_COLLECTION_ENABLED
export EVALUATION_COLLECTION_ENABLED
export EXPERIMENT_RUN_ID

if [ ! -f "$ENV_FILE" ]; then
    echo "Environment file not found: $ENV_FILE"
    exit 1
fi

print_experiment_configuration

# --------------------------------------------------
# Infrastructure
# --------------------------------------------------

ensure_network

start_blockchain

echo "Starting PostgreSQL and RabbitMQ..."
compose up -d postgres rabbitmq

wait_for_postgres
wait_for_rabbitmq

echo "Starting Axon Server..."
compose up -d axon-server

wait_for_container_health "$AXON_CONTAINER" 180

# --------------------------------------------------
# Applications
# --------------------------------------------------

echo "Building and starting consumer..."
compose up -d --build consumer-app

wait_for_processing_queues

echo "Building and starting producer..."
compose up -d --build producer-app

verify_producer_started

echo ""
echo "=================================================="
echo "All services started successfully"
echo "Run ID: $EXPERIMENT_RUN_ID"
echo "=================================================="