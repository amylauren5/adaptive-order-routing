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

DATA_DIR="$PROJECT_DIR/data"

. "$SCRIPTS_DIR/setup/common.sh"

# --------------------------------------------------
# Final evaluation configuration
# --------------------------------------------------

SEEDS="
1001
1002
1003
1004
1005
"

TOTAL_EXPERIMENTS=45
EXPERIMENT_NUMBER=0

# --------------------------------------------------
# Rotate strategy order between seeds
#
# This avoids always running one strategy first or
# last and reduces systematic run-order effects.
# --------------------------------------------------

strategies_for_seed() {
    seed="$1"

    case "$seed" in
        1001|1004)
            echo "shortest-queue little-law ml"
            ;;
        1002|1005)
            echo "little-law ml shortest-queue"
            ;;
        1003)
            echo "ml shortest-queue little-law"
            ;;
        *)
            echo "shortest-queue little-law ml"
            ;;
    esac
}

# --------------------------------------------------
# Verify required run outputs
# --------------------------------------------------

run_is_complete() {
    run_dir="$1"

    [ -s "$run_dir/run_metadata.json" ] \
        && [ -s "$run_dir/routing_metrics.csv" ] \
        && [ -s "$run_dir/queue_metrics.csv" ] \
        && [ -s "$run_dir/event_metrics.csv" ]
}

# --------------------------------------------------
# Run one workload condition across all seeds
# and all three routing strategies
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
    echo "Starting condition: $condition_name"
    echo "=================================================="
    echo ""

    for seed in $SEEDS; do

        STRATEGIES=$(
            strategies_for_seed "$seed"
        )

        for strategy in $STRATEGIES; do

            EXPERIMENT_NUMBER=$((EXPERIMENT_NUMBER + 1))

            RUN_ID=$(
                build_experiment_run_id \
                    "$strategy" \
                    "$scale" \
                    "$seed" \
                    "$burst_enabled" \
                    "$burst_multiplier" \
                    "$burst_duration" \
                    "$duration"
            )

            RUN_DIR="$DATA_DIR/$strategy/$RUN_ID"

            echo ""
            echo "##################################################"
            echo "Experiment $EXPERIMENT_NUMBER / $TOTAL_EXPERIMENTS"
            echo "##################################################"
            echo "Condition         : $condition_name"
            echo "Strategy          : $strategy"
            echo "Seed              : $seed"
            echo "Scale             : $scale"
            echo "Burst enabled     : $burst_enabled"
            echo "Burst multiplier  : $burst_multiplier"
            echo "Burst duration    : $burst_duration seconds"
            echo "Workload duration : $duration seconds"
            echo "Run ID            : $RUN_ID"
            echo "##################################################"
            echo ""

            # --------------------------------------------------
            # Skip already completed runs
            # --------------------------------------------------

            if [ -d "$RUN_DIR" ]; then

                if run_is_complete "$RUN_DIR"; then
                    echo "Completed run already exists."
                    echo "Skipping: $RUN_ID"
                    echo ""
                    continue
                fi

                echo "ERROR: Existing run directory appears incomplete:"
                echo "$RUN_DIR"
                echo ""
                echo "Refusing to overwrite or skip an incomplete run."
                echo "Inspect or remove this directory before continuing."
                exit 1
            fi

            # --------------------------------------------------
            # Reset previous experimental state
            # --------------------------------------------------

            "$SCRIPTS_DIR/teardown.sh"

            # --------------------------------------------------
            # Execute evaluation run
            # --------------------------------------------------

            "$SCRIPT_DIR/run-evaluation.sh" \
                "$strategy" \
                "$seed" \
                "$scale" \
                "$burst_enabled" \
                "$burst_multiplier" \
                "$burst_duration" \
                "$duration"

            # --------------------------------------------------
            # Verify output
            # --------------------------------------------------

            if ! run_is_complete "$RUN_DIR"; then
                echo ""
                echo "ERROR: Run reported completion but expected"
                echo "output files are missing or empty:"
                echo "$RUN_DIR"
                exit 1
            fi

            echo ""
            echo "Experiment $EXPERIMENT_NUMBER / $TOTAL_EXPERIMENTS"
            echo "completed successfully."
            echo "Preparing next run..."
            echo ""

            sleep 5
        done
    done
}

# ==================================================
# FINAL EVALUATION MATRIX
#
# 3 workload conditions
# × 5 seeds
# × 3 routing strategies
# = 45 final evaluation runs
# ==================================================

# --------------------------------------------------
# S1 — Medium steady control
#
# Same baseline scale used by both burst conditions.
# --------------------------------------------------

run_condition \
    "medium-steady-control" \
    5.0 \
    false \
    1.0 \
    5 \
    60

# --------------------------------------------------
# B1 — Strong short burst
#
# Inter-arrival times reduced to 25% of baseline
# for 5 seconds.
# --------------------------------------------------

run_condition \
    "strong-short-burst" \
    5.0 \
    true \
    0.25 \
    5 \
    60

# --------------------------------------------------
# B2 — Moderate long burst
#
# Inter-arrival times reduced to 50% of baseline
# for 10 seconds.
# --------------------------------------------------

run_condition \
    "moderate-long-burst" \
    5.0 \
    true \
    0.50 \
    10 \
    60

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
echo "FINAL EVALUATION MATRIX COMPLETED"
echo "=================================================="
echo "Completed matrix : $TOTAL_EXPERIMENTS / $TOTAL_EXPERIMENTS"
echo "Total time       : $FORMATTED_DURATION"
echo "Total seconds    : $TOTAL_DURATION_SECONDS"
echo "Data directory   : $DATA_DIR"
echo "=================================================="