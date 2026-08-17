#!/bin/sh

set -eu

SCRIPT_DIR=$(
    CDPATH= cd -- "$(dirname -- "$0")" && pwd
)

SCRIPTS_DIR=$(
    CDPATH= cd -- "$SCRIPT_DIR/.." && pwd
)

STRATEGIES="
shortest-queue
little-law
ml
"

SEEDS="
1001
1002
1003
1004
1005
"

run_condition() {
    scale="$1"
    burst_enabled="$2"
    burst_multiplier="$3"
    burst_duration="$4"
    duration="$5"

    for seed in $SEEDS; do
        for strategy in $STRATEGIES; do

            echo ""
            echo "##################################################"
            echo "Final evaluation"
            echo "Strategy         : $strategy"
            echo "Seed             : $seed"
            echo "Scale            : $scale"
            echo "Burst enabled    : $burst_enabled"
            echo "Burst multiplier : $burst_multiplier"
            echo "Burst duration   : $burst_duration"
            echo "Workload duration: $duration"
            echo "##################################################"
            echo ""

            "$SCRIPTS_DIR/teardown.sh"

            "$SCRIPT_DIR/run-evaluation.sh" \
                "$strategy" \
                "$seed" \
                "$scale" \
                "$burst_enabled" \
                "$burst_multiplier" \
                "$burst_duration" \
                "$duration"

            echo ""
            echo "Run finished successfully."
            echo "Preparing next run..."
            echo ""

            sleep 5
        done
    done
}

# S1 — Low steady
run_condition \
    8.0 \
    false \
    1.0 \
    5 \
    60

# S2 — Medium steady
run_condition \
    5.0 \
    false \
    1.0 \
    5 \
    60

# S3 — High steady
run_condition \
    3.0 \
    false \
    1.0 \
    5 \
    60

# B1 — Strong short burst
run_condition \
    5.0 \
    true \
    0.25 \
    5 \
    60

# B2 — Moderate long burst
run_condition \
    5.0 \
    true \
    0.50 \
    10 \
    60

echo ""
echo "=================================================="
echo "FINAL EVALUATION MATRIX COMPLETED"
echo "=================================================="