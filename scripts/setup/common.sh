#!/bin/sh

compose() {
    docker compose \
        -f "$COMPOSE_FILE" \
        --env-file "$ENV_FILE" \
        "$@"
}

validate_experiment_arguments() {
    case "$ROUTING_STRATEGY" in
        training|shortest-queue|little-law|ml)
            ;;
        *)
            echo "Invalid routing strategy: $ROUTING_STRATEGY"
            echo "Expected: training | shortest-queue | little-law | ml"
            exit 1
            ;;
    esac

    case "$WORKLOAD_BURST_ENABLED" in
        true|false)
            ;;
        *)
            echo "Burst enabled must be true or false."
            exit 1
            ;;
    esac
}

print_experiment_configuration() {
    echo ""
    echo "=================================================="
    echo "Experiment configuration"
    echo "Strategy          : $ROUTING_STRATEGY"
    echo "Seed              : $WORKLOAD_RANDOM_SEED"
    echo "Arrival scale     : $WORKLOAD_ARRIVAL_SCALE"
    echo "Burst enabled     : $WORKLOAD_BURST_ENABLED"
    echo "Burst multiplier  : $WORKLOAD_BURST_MULTIPLIER"
    echo "Duration          : $WORKLOAD_DURATION_SECONDS"
    echo "Run ID            : $EXPERIMENT_RUN_ID"
    echo "=================================================="
    echo ""
}

ensure_network() {
    echo "Ensuring Docker network exists..."

    if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
        docker network create "$NETWORK_NAME"
    else
        echo "Network '$NETWORK_NAME' already exists."
    fi
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
            healthy|running)
                echo "'$container_name' is ready."
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

verify_producer_started() {
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
}

set_env_value() {
    key="$1"
    value="$2"

    temporary_file="${ENV_FILE}.tmp"

    if [ -f "$ENV_FILE" ]; then
        grep -v "^${key}=" "$ENV_FILE" \
            > "$temporary_file" || true
    else
        : > "$temporary_file"
    fi

    printf '%s=%s\n' "$key" "$value" \
        >> "$temporary_file"

    mv "$temporary_file" "$ENV_FILE"
}

build_experiment_run_id() {
    strategy="$1"
    scale="$2"
    seed="$3"
    burst_enabled="$4"
    burst_multiplier="$5"
    duration="$6"

    if [ "$burst_enabled" = "true" ]; then
        burst_flag="1"
    else
        burst_flag="0"
    fi

    printf '%s-s%s-r%s-b%s-m%s-d%s\n' \
        "$strategy" \
        "$scale" \
        "$seed" \
        "$burst_flag" \
        "$burst_multiplier" \
        "$duration"
}