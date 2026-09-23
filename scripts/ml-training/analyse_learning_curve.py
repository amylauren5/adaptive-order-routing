from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

from train_models import (
    FEATURE_COLUMNS,
    TARGET_COLUMN,
    build_model,
    calculate_metrics,
    load_datasets,
    split_dataset,
    validate_dataset,
)


# ==================================================
# Selected XGBoost configuration
# ==================================================

SELECTED_XGBOOST_CONFIG = {
    "n_estimators": 700,
    "learning_rate": 0.03,
    "max_depth": 6,
    "min_child_weight": 4,
    "subsample": 0.9,
    "colsample_bytree": 0.9,
    "reg_lambda": 1.0,
}


# ==================================================
# Learning-curve configuration
# ==================================================

LEARNING_CURVE_RUN_COUNTS = [
    5,
    10,
    15,
    20,
]

DEFAULT_REPETITIONS = 20


# ==================================================
# Helpers
# ==================================================

def get_run_ids(
        data: pd.DataFrame,
) -> list[str]:
    return sorted(
        str(run_id)
        for run_id in data["run_id"].unique()
    )


def select_training_subset(
        development_data: pd.DataFrame,
        selected_runs: list[str],
) -> pd.DataFrame:
    return (
        development_data[
            development_data["run_id"].isin(
                selected_runs
            )
        ]
        .copy()
    )


def print_metrics(
        metrics: dict[str, float],
) -> None:
    print(
        f"MAE               : "
        f"{metrics['mae_ms']:.2f} ms"
    )
    print(
        f"RMSE              : "
        f"{metrics['rmse_ms']:.2f} ms"
    )
    print(
        f"R²                : "
        f"{metrics['r2']:.4f}"
    )
    print(
        f"Median abs. error : "
        f"{metrics['median_absolute_error_ms']:.2f} ms"
    )
    print(
        f"P95 abs. error    : "
        f"{metrics['p95_absolute_error_ms']:.2f} ms"
    )
    print(
        f"P99 abs. error    : "
        f"{metrics['p99_absolute_error_ms']:.2f} ms"
    )


# ==================================================
# Single model evaluation
# ==================================================

def evaluate_subset(
        training_subset: pd.DataFrame,
        test_data: pd.DataFrame,
        model_random_state: int,
) -> dict[str, float]:

    model = build_model(
        "XGBoost",
        SELECTED_XGBOOST_CONFIG,
        model_random_state,
    )

    model.fit(
        training_subset[FEATURE_COLUMNS],
        training_subset[TARGET_COLUMN],
    )

    predictions = model.predict(
        test_data[FEATURE_COLUMNS]
    )

    return calculate_metrics(
        test_data[TARGET_COLUMN],
        predictions,
    )


# ==================================================
# Repeated learning curve
# ==================================================

def run_learning_curve(
        development_data: pd.DataFrame,
        test_data: pd.DataFrame,
        subset_random_state: int,
        model_random_state: int,
        repetitions: int,
) -> pd.DataFrame:

    development_runs = get_run_ids(
        development_data
    )

    available_run_count = len(
        development_runs
    )

    requested_counts = [
        count
        for count in LEARNING_CURVE_RUN_COUNTS
        if count <= available_run_count
    ]

    if not requested_counts:
        raise ValueError(
            "Not enough development runs for "
            "the requested learning-curve points."
        )

    if repetitions < 1:
        raise ValueError(
            "repetitions must be at least 1."
        )

    rows: list[dict[str, object]] = []

    rng = np.random.default_rng(
        subset_random_state
    )

    print()
    print(
        "=========================================="
    )
    print(
        "Repeated learning-curve analysis"
    )
    print(
        "=========================================="
    )
    print(
        f"Development runs : "
        f"{available_run_count}"
    )
    print(
        f"Test runs        : "
        f"{test_data['run_id'].nunique()}"
    )
    print(
        f"Repetitions      : "
        f"{repetitions}"
    )

    smaller_counts = [
        count
        for count in requested_counts
        if count < available_run_count
    ]

    # --------------------------------------------------
    # Repeated random nested subsets
    # --------------------------------------------------

    for repetition in range(
            1,
            repetitions + 1,
    ):

        shuffled_runs = list(
            rng.permutation(
                development_runs
            )
        )

        print()
        print(
            "------------------------------------------"
        )
        print(
            f"Repetition {repetition}/{repetitions}"
        )
        print(
            "------------------------------------------"
        )

        for run_count in smaller_counts:

            selected_runs = [
                str(run_id)
                for run_id in shuffled_runs[
                              :run_count
                              ]
            ]

            training_subset = (
                select_training_subset(
                    development_data,
                    selected_runs,
                )
            )

            metrics = evaluate_subset(
                training_subset,
                test_data,
                model_random_state,
            )

            print(
                f"{run_count:2d} runs | "
                f"{len(training_subset):6,d} rows | "
                f"MAE {metrics['mae_ms']:8.2f} ms | "
                f"R² {metrics['r2']:7.4f}"
            )

            rows.append(
                {
                    "development_run_count": (
                        run_count
                    ),
                    "repetition": repetition,
                    "training_rows": len(
                        training_subset
                    ),
                    "test_runs": (
                        test_data[
                            "run_id"
                        ].nunique()
                    ),
                    "test_rows": len(
                        test_data
                    ),
                    **metrics,
                    "selected_run_ids": (
                        "|".join(
                            selected_runs
                        )
                    ),
                }
            )

    # --------------------------------------------------
    # Full development set
    #
    # There is only one possible 20-run subset, so there
    # is no reason to train the same model 20 times.
    # --------------------------------------------------

    if available_run_count in requested_counts:

        print()
        print(
            "=========================================="
        )
        print(
            f"Full {available_run_count}-run "
            f"development set"
        )
        print(
            "=========================================="
        )

        selected_runs = development_runs

        training_subset = (
            select_training_subset(
                development_data,
                selected_runs,
            )
        )

        metrics = evaluate_subset(
            training_subset,
            test_data,
            model_random_state,
        )

        print(
            f"Training rows: "
            f"{len(training_subset):,}"
        )

        print_metrics(
            metrics
        )

        rows.append(
            {
                "development_run_count": (
                    available_run_count
                ),
                "repetition": 1,
                "training_rows": len(
                    training_subset
                ),
                "test_runs": (
                    test_data[
                        "run_id"
                    ].nunique()
                ),
                "test_rows": len(
                    test_data
                ),
                **metrics,
                "selected_run_ids": (
                    "|".join(
                        selected_runs
                    )
                ),
            }
        )

    return pd.DataFrame(
        rows
    )


# ==================================================
# Summary
# ==================================================

def summarise_learning_curve(
        raw_results: pd.DataFrame,
) -> pd.DataFrame:

    metric_columns = [
        "training_rows",
        "mae_ms",
        "median_absolute_error_ms",
        "rmse_ms",
        "r2",
        "p95_absolute_error_ms",
        "p99_absolute_error_ms",
    ]

    summary_rows: list[
        dict[str, object]
    ] = []

    for run_count, group in raw_results.groupby(
            "development_run_count",
            sort=True,
    ):

        row: dict[str, object] = {
            "development_run_count": (
                int(run_count)
            ),
            "evaluations": len(group),
        }

        for metric in metric_columns:

            values = group[
                metric
            ].astype(float)

            row[
                f"{metric}_mean"
            ] = values.mean()

            row[
                f"{metric}_median"
            ] = values.median()

            row[
                f"{metric}_q1"
            ] = values.quantile(
                0.25
            )

            row[
                f"{metric}_q3"
            ] = values.quantile(
                0.75
            )

            row[
                f"{metric}_min"
            ] = values.min()

            row[
                f"{metric}_max"
            ] = values.max()

        summary_rows.append(
            row
        )

    return pd.DataFrame(
        summary_rows
    )


def print_summary(
        summary: pd.DataFrame,
) -> None:

    display_columns = [
        "development_run_count",
        "evaluations",
        "mae_ms_median",
        "mae_ms_q1",
        "mae_ms_q3",
        "rmse_ms_median",
        "r2_median",
        "p95_absolute_error_ms_median",
    ]

    print()
    print(
        "=========================================="
    )
    print(
        "Learning-curve summary"
    )
    print(
        "=========================================="
    )

    print(
        summary[
            display_columns
        ]
        .to_string(
            index=False,
            float_format=(
                lambda value:
                f"{value:.4f}"
            ),
        )
    )


# ==================================================
# Main
# ==================================================

def main() -> None:

    parser = argparse.ArgumentParser(
        description=(
            "Evaluate the selected XGBoost model "
            "using repeated run-level training subsets "
            "while preserving the original held-out "
            "test partition."
        )
    )

    parser.add_argument(
        "data_directory",
        type=Path,
        help=(
            "Directory containing run folders "
            "with training-dataset.csv."
        ),
    )

    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path(
            "scripts/ml-training/"
            "learning-curve-output"
        ),
    )

    parser.add_argument(
        "--validation-size",
        type=float,
        default=0.20,
    )

    parser.add_argument(
        "--test-size",
        type=float,
        default=0.20,
    )

    parser.add_argument(
        "--random-state",
        type=int,
        default=1001,
        help=(
            "Random state used to reproduce the "
            "original train/validation/test split."
        ),
    )

    parser.add_argument(
        "--subset-random-state",
        type=int,
        default=2026,
        help=(
            "Random state used only for repeated "
            "development-run subset selection."
        ),
    )

    parser.add_argument(
        "--repetitions",
        type=int,
        default=DEFAULT_REPETITIONS,
        help=(
            "Number of repeated random subsets "
            "for learning-curve points below the "
            "full development-set size."
        ),
    )

    args = parser.parse_args()

    data_directory = (
        args
        .data_directory
        .resolve()
    )

    output_directory = (
        args
        .output_directory
        .resolve()
    )

    if not data_directory.is_dir():
        raise NotADirectoryError(
            f"Invalid data directory: "
            f"{data_directory}"
        )

    output_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    # --------------------------------------------------
    # Load and validate using existing training pipeline
    # --------------------------------------------------

    data = load_datasets(
        data_directory
    )

    validate_dataset(
        data
    )

    # --------------------------------------------------
    # Recreate the original run-level split
    # --------------------------------------------------

    (
        train,
        validation,
        test,
        split_strategy,
    ) = split_dataset(
        data,
        validation_size=(
            args.validation_size
        ),
        test_size=(
            args.test_size
        ),
        random_state=(
            args.random_state
        ),
    )

    development_data = pd.concat(
        [
            train,
            validation,
        ],
        ignore_index=True,
    )

    # --------------------------------------------------
    # Sanity checks
    # --------------------------------------------------

    development_runs = set(
        get_run_ids(
            development_data
        )
    )

    test_runs = set(
        get_run_ids(
            test
        )
    )

    overlap = (
            development_runs
            & test_runs
    )

    if overlap:
        raise ValueError(
            "Development/test run leakage detected: "
            + ", ".join(
                sorted(
                    overlap
                )
            )
        )

    print()
    print(
        "=========================================="
    )
    print(
        "Dataset partition"
    )
    print(
        "=========================================="
    )

    print(
        f"Split strategy       : "
        f"{split_strategy}"
    )
    print(
        f"Initial training runs: "
        f"{train['run_id'].nunique()}"
    )
    print(
        f"Validation runs      : "
        f"{validation['run_id'].nunique()}"
    )
    print(
        f"Development runs     : "
        f"{development_data['run_id'].nunique()}"
    )
    print(
        f"Held-out test runs   : "
        f"{test['run_id'].nunique()}"
    )

    print()
    print(
        "Held-out test runs:"
    )

    for run_id in sorted(
            test_runs
    ):
        print(
            f"  - {run_id}"
        )

    # --------------------------------------------------
    # Evaluate
    # --------------------------------------------------

    raw_results = run_learning_curve(
        development_data=development_data,
        test_data=test,
        subset_random_state=(
            args.subset_random_state
        ),
        model_random_state=(
            args.random_state
        ),
        repetitions=(
            args.repetitions
        ),
    )

    summary = summarise_learning_curve(
        raw_results
    )

    # --------------------------------------------------
    # Save
    # --------------------------------------------------

    raw_output_path = (
            output_directory
            / "xgboost-learning-curve-raw.csv"
    )

    summary_output_path = (
            output_directory
            / "xgboost-learning-curve-summary.csv"
    )

    raw_results.to_csv(
        raw_output_path,
        index=False,
    )

    summary.to_csv(
        summary_output_path,
        index=False,
    )

    print_summary(
        summary
    )

    print()
    print(
        "=========================================="
    )
    print(
        "Output"
    )
    print(
        "=========================================="
    )

    print(
        f"Raw results : "
        f"{raw_output_path}"
    )
    print(
        f"Summary     : "
        f"{summary_output_path}"
    )

    print()
    print(
        "IMPORTANT:"
    )
    print(
        "The five held-out test runs remain "
        "excluded from all training subsets."
    )
    print(
        "Do not use the diagnostic test results "
        "to select a smaller final model."
    )
    print(
        "This analysis is intended to assess "
        "sensitivity to training-run coverage."
    )


if __name__ == "__main__":
    main()