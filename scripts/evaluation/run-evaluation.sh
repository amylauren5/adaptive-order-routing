#!/bin/sh

set -eu

SCRIPT_STARTED_AT=$(date +%s)

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

# --------------------------------------------------
# Run configuration
# --------------------------------------------------

STRATEGY="${1:-shortest-queue}"
SEED="${2:-1001}"
SCALE="${3:-5.0}"
BURST_ENABLED="${4:-false}"
BURST_MULTIPLIER="${5:-1.0}"
BURST_DURATION="${6:-5}"
DURATION="${7:-60}"

# --------------------------------------------------
# Validate strategy
# --------------------------------------------------

case "$STRATEGY" in
    shortest-queue|little-law|ml)
        ;;
    *)
        echo "ERROR: Invalid evaluation strategy: $STRATEGY"
        echo "Expected: shortest-queue | little-law | ml"
        exit 1
        ;;
esac

# --------------------------------------------------
# Data directory
# --------------------------------------------------

STRATEGY_DATA_DIR="$PROJECT_DIR/data/$STRATEGY"

mkdir -p "$STRATEGY_DATA_DIR"

export EXPERIMENT_DATA_DIR="$STRATEGY_DATA_DIR"

# --------------------------------------------------
# Build run identifier
# --------------------------------------------------

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

# --------------------------------------------------
# Prevent accidental overwrite
# --------------------------------------------------

if [ -d "$RUN_DIR" ]; then
    echo "ERROR: Run directory already exists:"
    echo "$RUN_DIR"
    echo ""
    echo "Refusing to overwrite an existing experiment."
    exit 1
fi

# --------------------------------------------------
# Start evaluation workload
# --------------------------------------------------

echo ""
echo "=================================================="
echo "Starting evaluation run"
echo "=================================================="
echo "Run ID            : $RUN_ID"
echo "Strategy          : $STRATEGY"
echo "Seed              : $SEED"
echo "Scale             : $SCALE"
echo "Burst enabled     : $BURST_ENABLED"
echo "Burst multiplier  : $BURST_MULTIPLIER"
echo "Burst duration    : $BURST_DURATION seconds"
echo "Workload duration : $DURATION seconds"
echo "Data directory    : $STRATEGY_DATA_DIR"
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

# --------------------------------------------------
# Wait for workload completion
# --------------------------------------------------

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

        echo ""
        echo "ERROR: Producer stopped before experiment completion."
        echo ""

        docker logs producer-app 2>/dev/null || true

        exit 1
    fi

    sleep 2
    elapsed=$((elapsed + 2))
done

if [ "$completed" != "true" ]; then
    echo ""
    echo "ERROR: Timed out waiting for experiment completion."
    echo ""

    docker logs producer-app 2>/dev/null || true

    exit 1
fi

# --------------------------------------------------
# Verify run directory
# --------------------------------------------------

if [ ! -d "$RUN_DIR" ]; then
    echo ""
    echo "ERROR: Expected run directory was not created:"
    echo "$RUN_DIR"
    exit 1
fi

# --------------------------------------------------
# Calculate total execution time
# --------------------------------------------------

SCRIPT_FINISHED_AT=$(date +%s)
TOTAL_DURATION_SECONDS=$((SCRIPT_FINISHED_AT - SCRIPT_STARTED_AT))

HOURS=$((TOTAL_DURATION_SECONDS / 3600))
MINUTES=$(((TOTAL_DURATION_SECONDS % 3600) / 60))
SECONDS=$((TOTAL_DURATION_SECONDS % 60))

FORMATTED_DURATION=$(printf "%02d:%02d:%02d" \
    "$HOURS" \
    "$MINUTES" \
    "$SECONDS")

# --------------------------------------------------
# Complete
# --------------------------------------------------

echo ""
echo "=================================================="
echo "Evaluation run completed successfully"
echo "=================================================="
echo "Run ID        : $RUN_ID"
echo "Strategy      : $STRATEGY"
echo "Data          : $RUN_DIR"
echo "Execution time: $FORMATTED_DURATION"
echo "Total seconds : $TOTAL_DURATION_SECONDS"
echo "=================================================="