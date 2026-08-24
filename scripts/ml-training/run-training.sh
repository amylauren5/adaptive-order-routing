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

VENV_PYTHON="$PROJECT_DIR/.venv/bin/python"

# --------------------------------------------------
# Preconditions
# --------------------------------------------------

if [ ! -x "$VENV_PYTHON" ]; then
    echo "ERROR: Python virtual environment not found:"
    echo "$VENV_PYTHON"
    echo ""
    echo "Create it and install dependencies before running:"
    echo "  python3 -m venv .venv"
    echo "  .venv/bin/pip install -r scripts/ml-training/requirements.txt"
    exit 1
fi

if ! "$VENV_PYTHON" -c "import numpy, pandas, sklearn, xgboost" >/dev/null 2>&1; then
    echo "ERROR: Required Python ML dependencies are missing."
    echo ""
    echo "Install them with:"
    echo "  .venv/bin/pip install -r scripts/ml-training/requirements.txt"
    exit 1
fi

# --------------------------------------------------
# Prepare training data directory
# --------------------------------------------------

DATA_DIR="${EXPERIMENT_DATA_DIR:-$PROJECT_DIR/data/training}"

mkdir -p "$DATA_DIR"

export EXPERIMENT_DATA_DIR="$DATA_DIR"

# --------------------------------------------------
# Common experiment helpers
# --------------------------------------------------

. "$SCRIPTS_DIR/setup/common.sh"

# --------------------------------------------------
# Training run configuration
# --------------------------------------------------

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

RUN_DIR="$DATA_DIR/$RUN_ID"

# --------------------------------------------------
# Prevent accidental overwrite
# --------------------------------------------------

if [ -d "$RUN_DIR" ]; then
    echo "ERROR: Training run directory already exists:"
    echo "$RUN_DIR"
    echo ""
    echo "Remove or archive it before rerunning this configuration."
    exit 1
fi

# --------------------------------------------------
# Start training workload
# --------------------------------------------------

echo ""
echo "=================================================="
echo "Starting training run"
echo "=================================================="
echo "Run ID            : $RUN_ID"
echo "Seed              : $SEED"
echo "Scale             : $SCALE"
echo "Burst enabled     : $BURST_ENABLED"
echo "Burst multiplier  : $BURST_MULTIPLIER"
echo "Burst duration    : $BURST_DURATION seconds"
echo "Workload duration : $DURATION seconds"
echo "Data directory    : $DATA_DIR"
echo "Python            : $VENV_PYTHON"
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

# --------------------------------------------------
# Wait for workload completion
# --------------------------------------------------

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
# Verify training run output
# --------------------------------------------------

if [ ! -d "$RUN_DIR" ]; then
    echo ""
    echo "ERROR: Expected training run directory was not created:"
    echo "$RUN_DIR"
    exit 1
fi

# --------------------------------------------------
# Build joined training dataset
# --------------------------------------------------

echo ""
echo "Building training dataset..."

"$VENV_PYTHON" \
    "$SCRIPT_DIR/build_training_datasets.py" \
    "$DATA_DIR" \
    --run-id "$RUN_ID"

TRAINING_DATASET="$RUN_DIR/training-dataset.csv"

# --------------------------------------------------
# Verify training dataset
# --------------------------------------------------

if [ ! -s "$TRAINING_DATASET" ]; then
    echo ""
    echo "ERROR: Training dataset was not created or is empty:"
    echo "$TRAINING_DATASET"
    exit 1
fi

# --------------------------------------------------
# Calculate total script duration
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
# Final summary
# --------------------------------------------------

echo ""
echo "=================================================="
echo "TRAINING RUN COMPLETED SUCCESSFULLY"
echo "=================================================="
echo "Run ID          : $RUN_ID"
echo "Dataset         : $TRAINING_DATASET"
echo "Script duration : $FORMATTED_DURATION"
echo "Total seconds   : $TOTAL_DURATION_SECONDS"
echo "=================================================="