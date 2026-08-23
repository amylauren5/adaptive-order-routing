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

DATA_DIR="${EXPERIMENT_DATA_DIR:-$PROJECT_DIR/data/training}"

mkdir -p "$DATA_DIR"

export EXPERIMENT_DATA_DIR="$DATA_DIR"

. "$SCRIPTS_DIR/setup/common.sh"

SEED="${1:-1002}"
SCALE="${2:-2.0}"
BURST_ENABLED="${3:-false}"
BURST_MULTIPLIER="${4:-1.0}"
BURST_DURATION="${5:-5}"
DURATION="${6:-30}"

RUN_ID=$(
    build_experiment_run_id \
        "training" \
        "$SCALE" \
        "$SEED" \
        "$BURST_ENABLED" \
        "$BURST_MULTIPLIER" \
        "$BURST_DURATION" \
        "$DURATION"
)

echo ""
echo "=================================================="
echo "Starting training run"
echo "Run ID: $RUN_ID"
echo "=================================================="
echo ""

"$SCRIPTS_DIR/start.sh" \
    training \
    "$SEED" \
    "$SCALE" \
    "$BURST_ENABLED" \
    "$BURST_MULTIPLIER" \
    "$BURST_DURATION" \
    "$DURATION"

echo ""
echo "Waiting for experiment to complete..."

timeout_seconds=$((DURATION + 600))
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

echo ""
echo "Building training dataset..."

python3 "$PROJECT_DIR/scripts/ml-training/build_training_datasets.py" \
    "$DATA_DIR" \
    --run-id "$RUN_ID"

echo ""
echo "=================================================="
echo "Training run completed successfully"
echo "Dataset: $DATA_DIR/$RUN_ID/training-dataset.csv"
echo "=================================================="