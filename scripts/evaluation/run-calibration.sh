#!/bin/sh

set -eu

echo "========================================"
echo "Running steady workload calibration"
echo "========================================"

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 8.0 false 1.0 5 60

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 7.0 false 1.0 5 60

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 5.0 false 1.0 5 60

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 3.0 false 1.0 5 60

./scripts/teardown.sh

echo "========================================"
echo "Running burst workload calibration"
echo "========================================"

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 5.0 true 0.25 5 60

./scripts/evaluation/run-evaluation.sh \
    shortest-queue 9001 5.0 true 0.50 10 60

echo "========================================"
echo "Analysing calibration runs"
echo "========================================"

.venv/bin/python \
    scripts/evaluation/analyse_experiments.py \
    --data-dir data

echo "========================================"
echo "Calibration complete"
echo "========================================"