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

DATA_DIR="$PROJECT_DIR/data/training"
MODEL_OUTPUT_DIR="$PROJECT_DIR/scripts/ml-training/output"
PRODUCER_MODEL_DIR="$PROJECT_DIR/order-routing-producer/src/main/resources/models"
VENV_PYTHON="$PROJECT_DIR/.venv/bin/python"

# --------------------------------------------------
# Final training configuration
# --------------------------------------------------

TRAINING_SEEDS="
2001
2002
2003
2004
2005
"

TOTAL_TRAINING_RUNS=25
TRAINING_RUN_NUMBER=0

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

if [ ! -x "$SCRIPTS_DIR/teardown.sh" ]; then
    echo "ERROR: Teardown script not found or not executable:"
    echo "$SCRIPTS_DIR/teardown.sh"
    exit 1
fi

if [ ! -x "$SCRIPT_DIR/run-training.sh" ]; then
    echo "ERROR: Training script not found or not executable:"
    echo "$SCRIPT_DIR/run-training.sh"
    exit 1
fi

if [ ! -f "$SCRIPT_DIR/train_models.py" ]; then
    echo "ERROR: Model training script not found:"
    echo "$SCRIPT_DIR/train_models.py"
    exit 1
fi

# --------------------------------------------------
# Prepare training data directory
# --------------------------------------------------

mkdir -p "$DATA_DIR"

if [ -n "$(find "$DATA_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    echo "ERROR: Training data directory is not empty:"
    echo "$DATA_DIR"
    echo ""
    echo "Archive or remove existing pilot/training runs before"
    echo "starting the final training pipeline."
    exit 1
fi

# --------------------------------------------------
# Helper: run one workload condition across all seeds
# --------------------------------------------------

run_condition() {
    condition_name="$1"
    scale="$2"
    burst_enabled="$3"
    burst_multiplier="$4"
    burst_duration="$5"
    duration="$6"

    echo ""
    echo "=================================================="
    echo "Starting training condition: $condition_name"
    echo "=================================================="
    echo ""

    for seed in $TRAINING_SEEDS; do

        TRAINING_RUN_NUMBER=$((TRAINING_RUN_NUMBER + 1))

        echo ""
        echo "##################################################"
        echo "Final training data collection"
        echo "##################################################"
        echo "Training run      : $TRAINING_RUN_NUMBER / $TOTAL_TRAINING_RUNS"
        echo "Condition         : $condition_name"
        echo "Seed              : $seed"
        echo "Scale             : $scale"
        echo "Burst enabled     : $burst_enabled"
        echo "Burst multiplier  : $burst_multiplier"
        echo "Burst duration    : $burst_duration seconds"
        echo "Workload duration : $duration seconds"
        echo "##################################################"
        echo ""

        "$SCRIPTS_DIR/teardown.sh"

        "$SCRIPT_DIR/run-training.sh" \
            "$seed" \
            "$scale" \
            "$burst_enabled" \
            "$burst_multiplier" \
            "$burst_duration" \
            "$duration"

        echo ""
        echo "Training run $TRAINING_RUN_NUMBER / $TOTAL_TRAINING_RUNS"
        echo "finished successfully."
        echo "Preparing next run..."
        echo ""

        sleep 5
    done
}

# --------------------------------------------------
# Start final training collection
# --------------------------------------------------

echo ""
echo "=================================================="
echo "FINAL TRAINING DATA COLLECTION"
echo "=================================================="
echo "Project directory : $PROJECT_DIR"
echo "Data directory    : $DATA_DIR"
echo "Python            : $VENV_PYTHON"
echo "Expected runs     : $TOTAL_TRAINING_RUNS"
echo "=================================================="
echo ""

# --------------------------------------------------
# Training workload matrix
#
# 5 workload conditions
# × 5 training seeds
# = 25 training runs
# --------------------------------------------------

# S1 — Low steady
run_condition \
    "low-steady" \
    8.0 \
    false \
    1.0 \
    5 \
    60

# S2 — Medium steady
run_condition \
    "medium-steady" \
    5.0 \
    false \
    1.0 \
    5 \
    60

# S3 — High steady
run_condition \
    "high-steady" \
    3.0 \
    false \
    1.0 \
    5 \
    60

# B1 — Strong short burst
run_condition \
    "strong-short-burst" \
    5.0 \
    true \
    0.25 \
    5 \
    60

# B2 — Moderate long burst
run_condition \
    "moderate-long-burst" \
    5.0 \
    true \
    0.50 \
    10 \
    60

echo ""
echo "=================================================="
echo "FINAL TRAINING DATA COLLECTION COMPLETED"
echo "Completed runs: $TRAINING_RUN_NUMBER / $TOTAL_TRAINING_RUNS"
echo "=================================================="
echo ""

# --------------------------------------------------
# Stop experiment infrastructure before model training
# --------------------------------------------------

echo ""
echo "Stopping experiment infrastructure before model training..."
echo ""

"$SCRIPTS_DIR/teardown.sh"

# --------------------------------------------------
# Train final ML models once using all training runs
# --------------------------------------------------

echo ""
echo "=================================================="
echo "Training final ML models"
echo "=================================================="
echo ""

"$VENV_PYTHON" \
    "$SCRIPT_DIR/train_models.py" \
    "$DATA_DIR"

echo ""
echo "Model training completed successfully."
echo ""

# --------------------------------------------------
# Verify model outputs
# --------------------------------------------------

if [ ! -d "$MODEL_OUTPUT_DIR" ]; then
    echo "ERROR: Model output directory was not created:"
    echo "$MODEL_OUTPUT_DIR"
    exit 1
fi

XGBOOST_MODEL="$MODEL_OUTPUT_DIR/xgboost-model.json"
MODEL_SCHEMA="$MODEL_OUTPUT_DIR/model-schema.json"

if [ ! -s "$XGBOOST_MODEL" ]; then
    echo "ERROR: XGBoost model was not created or is empty:"
    echo "$XGBOOST_MODEL"
    exit 1
fi

if [ ! -s "$MODEL_SCHEMA" ]; then
    echo "ERROR: Model schema was not created or is empty:"
    echo "$MODEL_SCHEMA"
    exit 1
fi

echo "Model training outputs:"
ls -lah "$MODEL_OUTPUT_DIR"

# --------------------------------------------------
# Copy runtime ML artifacts into producer resources
# --------------------------------------------------

mkdir -p "$PRODUCER_MODEL_DIR"

echo ""
echo "Copying runtime ML artifacts..."
echo "Producer model directory:"
echo "$PRODUCER_MODEL_DIR"
echo ""

cp "$XGBOOST_MODEL" \
    "$PRODUCER_MODEL_DIR/xgboost-model.json"

cp "$MODEL_SCHEMA" \
    "$PRODUCER_MODEL_DIR/model-schema.json"

echo "Copied:"
echo "  xgboost-model.json"
echo "  model-schema.json"

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
# Final summary
# --------------------------------------------------

echo ""
echo "=================================================="
echo "FINAL TRAINING PIPELINE COMPLETED"
echo "=================================================="
echo "Training runs : $TRAINING_RUN_NUMBER / $TOTAL_TRAINING_RUNS"
echo "Total time    : $FORMATTED_DURATION"
echo "Total seconds : $TOTAL_DURATION_SECONDS"
echo "Model outputs : $MODEL_OUTPUT_DIR"
echo "Producer model directory:"
echo "$PRODUCER_MODEL_DIR"
echo "=================================================="