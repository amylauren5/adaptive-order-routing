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

DATA_DIR="$PROJECT_DIR/data/training"
MODEL_OUTPUT_DIR="$PROJECT_DIR/scripts/ml-training/output"
PRODUCER_MODEL_DIR="$PROJECT_DIR/order-routing-producer/src/main/resources/models"
VENV_PYTHON="$PROJECT_DIR/.venv/bin/python"

# --------------------------------------------------
# Final training seeds
# --------------------------------------------------

TRAINING_SEEDS="
2001
2002
2003
2004
2005
"

# --------------------------------------------------
# Preconditions
# --------------------------------------------------

if [ ! -x "$VENV_PYTHON" ]; then
    echo "Python virtual environment not found:"
    echo "$VENV_PYTHON"
    exit 1
fi

if [ ! -d "$DATA_DIR" ]; then
    echo "Data directory does not exist:"
    echo "$DATA_DIR"
    exit 1
fi

if [ ! -x "$SCRIPTS_DIR/teardown.sh" ]; then
    echo "Teardown script not found or not executable:"
    echo "$SCRIPTS_DIR/teardown.sh"
    exit 1
fi

if [ ! -x "$SCRIPT_DIR/run-training.sh" ]; then
    echo "Training script not found or not executable:"
    echo "$SCRIPT_DIR/run-training.sh"
    exit 1
fi

if [ ! -f "$SCRIPT_DIR/train_models.py" ]; then
    echo "Model training script not found:"
    echo "$SCRIPT_DIR/train_models.py"
    exit 1
fi

# --------------------------------------------------
# Helper: run one workload condition across all seeds
# --------------------------------------------------

run_condition() {
    scale="$1"
    burst_enabled="$2"
    burst_multiplier="$3"
    burst_duration="$4"
    duration="$5"

    for seed in $TRAINING_SEEDS; do

        echo ""
        echo "##################################################"
        echo "Final training data collection"
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
        echo "Training run finished successfully."
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
echo "=================================================="
echo ""

# --------------------------------------------------
# Training workload matrix
# --------------------------------------------------

# Low steady
run_condition \
    8.0 \
    false \
    1.0 \
    5 \
    60

# Medium steady
run_condition \
    5.0 \
    false \
    1.0 \
    5 \
    60

# High steady
run_condition \
    3.0 \
    false \
    1.0 \
    5 \
    60

# Strong short burst
run_condition \
    5.0 \
    true \
    0.25 \
    5 \
    60

# Moderate long burst
run_condition \
    5.0 \
    true \
    0.50 \
    10 \
    60

echo ""
echo "=================================================="
echo "FINAL TRAINING DATA COLLECTION COMPLETED"
echo "=================================================="
echo ""

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
    echo "Model output directory was not created:"
    echo "$MODEL_OUTPUT_DIR"
    exit 1
fi

XGBOOST_MODEL="$MODEL_OUTPUT_DIR/xgboost-model.json"
MODEL_SCHEMA="$MODEL_OUTPUT_DIR/model-schema.json"

if [ ! -f "$XGBOOST_MODEL" ]; then
    echo "XGBoost model was not created:"
    echo "$XGBOOST_MODEL"
    exit 1
fi

if [ ! -f "$MODEL_SCHEMA" ]; then
    echo "Model schema was not created:"
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
# Final summary
# --------------------------------------------------

echo ""
echo "=================================================="
echo "FINAL TRAINING PIPELINE COMPLETED"
echo "=================================================="
echo "Training runs : 25"
echo "Model outputs : $MODEL_OUTPUT_DIR"
echo "Producer model directory:"
echo "$PRODUCER_MODEL_DIR"
echo "=================================================="