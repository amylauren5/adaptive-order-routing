#!/bin/sh

set -eu

SCRIPT_DIR=$(
    CDPATH= cd -- "$(dirname -- "$0")" && pwd
)

SCRIPTS_DIR=$(
    CDPATH= cd -- "$SCRIPT_DIR/.." && pwd
)

PROJECT_DIR=$(
    CDPATH= cd -- "$SCRIPTS_DIR/.." && pwd
)

. "$SCRIPTS_DIR/setup/common.sh"

STRATEGY="${1:-shortest-queue}"
SEED="${2:-1001}"
SCALE="${3:-5.0}"
BURST_ENABLED="${4:-false}"
BURST_MULTIPLIER="${5:-1.0}"
BURST_DURATION="${6:-5}"
DURATION="${7:-60}"

case "$STRATEGY" in
    shortest-queue|little-law|ml)
        ;;
    *)
        echo "Invalid evaluation strategy: $STRATEGY"
        echo "Expected: shortest-queue | little-law | ml"
        exit 1
        ;;
esac

STRATEGY_DATA_DIR="$PROJECT_DIR/data/$STRATEGY"

mkdir -p "$STRATEGY_DATA_DIR"

export EXPERIMENT_DATA_DIR="$STRATEGY_DATA_DIR"

RUN_ID=$(
    build_experiment_run_id \
        "$STRATEGY" \
        "$SCALE" \
        "$SEED" \
        "$BURST_ENABLED" \
        "$BURST_MULTIPLIER" \
        "$BURST_DURATION" \
        "$DURATION"
)

RUN_DIR="$STRATEGY_DATA_DIR/$RUN_ID"

if [ -d "$RUN_DIR" ]; then
    echo "Run directory already exists:"
    echo "$RUN_DIR"
    echo ""
    echo "Refusing to overwrite an existing experiment."
    exit 1
fi

echo ""
echo "=================================================="
echo "Starting evaluation run"
echo "Run ID: $RUN_ID"
echo "=================================================="
echo ""

"$SCRIPTS_DIR/start.sh" \
    "$STRATEGY" \
    "$SEED" \
    "$SCALE" \
    "$BURST_ENABLED" \
    "$BURST_MULTIPLIER" \
    "$BURST_DURATION" \
    "$DURATION"

echo ""
echo "Waiting for experiment to complete..."

timeout_seconds=$((DURATION + 900))
elapsed=0
completed="false"

while [ "$elapsed" -lt "$timeout_seconds" ]; do

    if docker logs producer-app 2>&1 \
        | grep -q "Experiment completed successfully"; then

        completed="true"
        echo "Experiment completed successfully."
        break
    fi

    producer_status=$(
        docker inspect \
            --format='{{.State.Status}}' \
            producer-app 2>/dev/null || true
    )

    if [ "$producer_status" = "exited" ] \
        || [ "$producer_status" = "dead" ]; then

        echo "Producer stopped before experiment completion."
        docker logs producer-app 2>/dev/null || true
        exit 1
    fi

    sleep 2
    elapsed=$((elapsed + 2))
done

if [ "$completed" != "true" ]; then
    echo "Timed out waiting for experiment completion."
    docker logs producer-app 2>/dev/null || true
    exit 1
fi

if [ ! -d "$RUN_DIR" ]; then
    echo "Expected run directory was not created:"
    echo "$RUN_DIR"
    exit 1
fi

echo ""
echo "=================================================="
echo "Evaluation run completed successfully"
echo "Run ID: $RUN_ID"
echo "Data: $RUN_DIR"
echo "=================================================="