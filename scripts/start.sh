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

compose() {
    docker compose \
        -f "$COMPOSE_FILE" \
        --env-file "$ENV_FILE" \
        "$@"
}

wait_for_postgres() {
    timeout_seconds=120
    elapsed=0

    echo "Waiting for PostgreSQL..."

    while [ "$elapsed" -lt "$timeout_seconds" ]; do
        if docker exec "$POSTGRES_CONTAINER" sh -c \
            'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
            >/dev/null 2>&1; then

            echo "PostgreSQL is ready."
            return 0
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "Timed out waiting for PostgreSQL."
    docker logs "$POSTGRES_CONTAINER" 2>/dev/null || true
    return 1
}

wait_for_rabbitmq() {
    timeout_seconds=120
    elapsed=0

    echo "Waiting for RabbitMQ..."

    while [ "$elapsed" -lt "$timeout_seconds" ]; do
        if docker exec "$RABBITMQ_CONTAINER" \
            rabbitmq-diagnostics -q ping \
            >/dev/null 2>&1; then

            echo "RabbitMQ is ready."
            return 0
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "Timed out waiting for RabbitMQ."
    docker logs "$RABBITMQ_CONTAINER" 2>/dev/null || true
    return 1
}

wait_for_container_health() {
    container_name="$1"
    timeout_seconds="${2:-180}"
    elapsed=0

    echo "Waiting for '$container_name'..."

    while [ "$elapsed" -lt "$timeout_seconds" ]; do
        status=$(
            docker inspect \
                --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
                "$container_name" 2>/dev/null || true
        )

        case "$status" in
            healthy)
                echo "'$container_name' is healthy."
                return 0
                ;;
            exited|dead)
                echo "'$container_name' stopped unexpectedly."
                docker logs "$container_name" 2>/dev/null || true
                return 1
                ;;
        esac

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "Timed out waiting for '$container_name'."
    docker logs "$container_name" 2>/dev/null || true
    return 1
}

wait_for_ganache() {
    timeout_seconds=60
    elapsed=0

    echo "Waiting for Ganache RPC..."

    while [ "$elapsed" -lt "$timeout_seconds" ]; do
        if docker run --rm \
            --network "$NETWORK_NAME" \
            curlimages/curl:latest \
            --silent \
            --fail \
            --request POST \
            --header "Content-Type: application/json" \
            --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' \
            "http://ganache:8545" \
            >/dev/null 2>&1; then

            echo "Ganache is ready."
            return 0
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "Timed out waiting for Ganache."
    docker logs "$GANACHE_CONTAINER" 2>/dev/null || true
    return 1
}

wait_for_processing_queues() {
    timeout_seconds=180
    elapsed=0

    echo "Waiting for RabbitMQ processing queues..."

    while [ "$elapsed" -lt "$timeout_seconds" ]; do
        queues=$(
            docker exec "$RABBITMQ_CONTAINER" \
                rabbitmqctl -q list_queues name \
                2>/dev/null || true
        )

        if printf '%s\n' "$queues" | grep -qx "processing.queue-1" \
            && printf '%s\n' "$queues" | grep -qx "processing.queue-2" \
            && printf '%s\n' "$queues" | grep -qx "processing.queue-3"; then

            echo "All processing queues are ready."
            return 0
        fi

        consumer_status=$(
            docker inspect \
                --format='{{.State.Status}}' \
                "$CONSUMER_CONTAINER" 2>/dev/null || true
        )

        if [ "$consumer_status" = "exited" ] \
            || [ "$consumer_status" = "dead" ]; then

            echo "Consumer stopped before declaring the queues."
            docker logs "$CONSUMER_CONTAINER" 2>/dev/null || true
            return 1
        fi

        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo "Timed out waiting for processing queues."
    docker logs "$CONSUMER_CONTAINER" 2>/dev/null || true
    return 1
}

if [ ! -f "$ENV_FILE" ]; then
    echo "Environment file not found: $ENV_FILE"
    exit 1
fi

echo "Ensuring Docker network exists..."

if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
    docker network create "$NETWORK_NAME"
else
    echo "Network '$NETWORK_NAME' already exists."
fi

echo "Removing previous Ganache container..."
docker rm -f "$GANACHE_CONTAINER" >/dev/null 2>&1 || true

echo "Starting Ganache..."

docker run -d \
    --name "$GANACHE_CONTAINER" \
    --network "$NETWORK_NAME" \
    -p 8545:8545 \
    trufflesuite/ganache-cli \
    --gasLimit 12000000 \
    --accounts 10 \
    --defaultBalanceEther 100

wait_for_ganache

echo "Extracting Ganache contract information..."

docker run --rm \
    --network "$NETWORK_NAME" \
    -v "$PROJECT_DIR:/scripts" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -w /scripts/scripts/setup \
    docker:stable \
    sh ./extract-ganache.sh

echo "Starting PostgreSQL and RabbitMQ..."

compose up -d postgres rabbitmq

wait_for_postgres
wait_for_rabbitmq

echo "Starting Axon Server..."

compose up -d axon-server

wait_for_container_health "$AXON_CONTAINER" 180

echo "Building and starting consumer..."

compose up -d --build consumer-app

wait_for_processing_queues

echo "Building and starting producer..."

compose up -d --build producer-app

producer_status=$(
    docker inspect \
        --format='{{.State.Status}}' \
        "$PRODUCER_CONTAINER" 2>/dev/null || true
)

if [ "$producer_status" != "running" ]; then
    echo "Producer failed to start."
    docker logs "$PRODUCER_CONTAINER" 2>/dev/null || true
    exit 1
fi

echo ""
echo "=================================================="
echo "All services started successfully"
echo "=================================================="