from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    r2_score,
)
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBRegressor


# ==================================================
# Dataset schema
# ==================================================

TARGET_COLUMN = "realised_waiting_time_ms"

CATEGORICAL_COLUMNS = [
    "order_status",
    "category",
]

NUMERIC_COLUMNS = [
    "order_value",
    "item_count",
    "candidate_queue_length",
    "candidate_arrival_rate",
    "candidate_consumer_throughput",
    "candidate_utilisation",
    "candidate_backlog_growth",
]

FEATURE_COLUMNS = (
        CATEGORICAL_COLUMNS
        + NUMERIC_COLUMNS
)

TRACEABILITY_COLUMNS = [
    "run_id",
    "routing_decision_id",
    "order_id",
    "order_status",
    "selected_queue",
    TARGET_COLUMN,
]


# ==================================================
# Predefined hyperparameter configurations
# ==================================================

RANDOM_FOREST_CONFIGS = [
    {
        "n_estimators": 300,
        "min_samples_leaf": 1,
        "max_features": "sqrt",
    },
    {
        "n_estimators": 400,
        "min_samples_leaf": 2,
        "max_features": "sqrt",
    },
    {
        "n_estimators": 500,
        "min_samples_leaf": 4,
        "max_features": 1.0,
    },
]

XGBOOST_CONFIGS = [
    {
        "n_estimators": 300,
        "learning_rate": 0.05,
        "max_depth": 4,
        "min_child_weight": 2,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "reg_lambda": 1.0,
    },
    {
        "n_estimators": 500,
        "learning_rate": 0.05,
        "max_depth": 6,
        "min_child_weight": 2,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "reg_lambda": 1.0,
    },
    {
        "n_estimators": 700,
        "learning_rate": 0.03,
        "max_depth": 6,
        "min_child_weight": 4,
        "subsample": 0.9,
        "colsample_bytree": 0.9,
        "reg_lambda": 1.0,
    },
]


# ==================================================
# Dataset loading
# ==================================================

def load_datasets(
        data_directory: Path,
) -> pd.DataFrame:
    dataset_paths = sorted(
        data_directory.glob(
            "*/training-dataset.csv"
        )
    )

    if not dataset_paths:
        raise FileNotFoundError(
            "No training-dataset.csv files found "
            f"under {data_directory}"
        )

    frames: list[pd.DataFrame] = []

    for dataset_path in dataset_paths:
        frame = pd.read_csv(
            dataset_path
        )

        frame["run_id"] = (
            dataset_path.parent.name
        )

        frames.append(
            frame
        )

        print(
            f"Loaded {len(frame):,} rows from "
            f"'{dataset_path.parent.name}'"
        )

    combined = pd.concat(
        frames,
        ignore_index=True,
    )

    print(
        f"Combined dataset: {len(combined):,} rows "
        f"from {combined['run_id'].nunique()} run(s)"
    )

    return combined


# ==================================================
# Dataset validation
# ==================================================

def validate_dataset(
        data: pd.DataFrame,
) -> None:
    required_columns = {
        "run_id",
        "routing_decision_id",
        "order_id",
        "selected_queue",
        TARGET_COLUMN,
        *FEATURE_COLUMNS,
    }

    missing_columns = sorted(
        required_columns
        - set(data.columns)
    )

    if missing_columns:
        raise ValueError(
            "Dataset is missing required columns: "
            f"{missing_columns}"
        )

    duplicate_count = int(
        data[
            "routing_decision_id"
        ]
        .duplicated()
        .sum()
    )

    if duplicate_count:
        raise ValueError(
            f"Dataset contains {duplicate_count} "
            "duplicate routing decision IDs"
        )

    if data[
        TARGET_COLUMN
    ].isna().any():
        raise ValueError(
            f"{TARGET_COLUMN} contains "
            "missing values"
        )

    if (
            data[
                TARGET_COLUMN
            ] < 0
    ).any():
        raise ValueError(
            f"{TARGET_COLUMN} contains "
            "negative values"
        )

    missing_features = (
        data[
            FEATURE_COLUMNS
        ]
        .isna()
        .sum()
    )

    missing_features = (
        missing_features[
            missing_features > 0
            ]
    )

    if not missing_features.empty:
        raise ValueError(
            "Feature columns contain "
            "missing values:\n"
            f"{missing_features.to_string()}"
        )

    numeric_values = (
        data[
            NUMERIC_COLUMNS
        ]
        .to_numpy(
            dtype=float
        )
    )

    if not np.isfinite(
            numeric_values
    ).all():
        raise ValueError(
            "Numeric features contain "
            "non-finite values"
        )


# ==================================================
# Run-level dataset partitioning
# ==================================================

def split_dataset(
        data: pd.DataFrame,
        validation_size: float,
        test_size: float,
        random_state: int,
) -> tuple[
    pd.DataFrame,
    pd.DataFrame,
    pd.DataFrame,
    str,
]:
    if (
            validation_size <= 0
            or test_size <= 0
    ):
        raise ValueError(
            "Validation and test sizes "
            "must be positive"
        )

    if (
            validation_size
            + test_size
            >= 1
    ):
        raise ValueError(
            "Validation size plus test size "
            "must be less than 1"
        )

    run_count = int(
        data[
            "run_id"
        ].nunique()
    )

    if run_count >= 3:
        grouping_column = "run_id"

        split_strategy = (
            "complete experiment runs"
        )

    else:
        grouping_column = "order_id"

        split_strategy = (
            "complete orders "
            "(development fallback only)"
        )

        print(
            "\nWARNING: Fewer than three complete "
            "runs were found.\n"
            "The script will split by complete "
            "order_id groups only to verify that "
            "the ML pipeline works.\n"
            "Do not report these scores as final "
            "thesis results.\n"
        )

    groups = data[
        grouping_column
    ]

    holdout_size = (
            validation_size
            + test_size
    )

    first_split = GroupShuffleSplit(
        n_splits=1,
        test_size=holdout_size,
        random_state=random_state,
    )

    (
        train_indices,
        holdout_indices,
    ) = next(
        first_split.split(
            data,
            groups=groups,
        )
    )

    train = (
        data.iloc[
            train_indices
        ]
        .copy()
    )

    holdout = (
        data.iloc[
            holdout_indices
        ]
        .copy()
    )

    relative_test_size = (
            test_size
            / holdout_size
    )

    second_split = GroupShuffleSplit(
        n_splits=1,
        test_size=relative_test_size,
        random_state=(
                random_state + 1
        ),
    )

    (
        validation_indices,
        test_indices,
    ) = next(
        second_split.split(
            holdout,
            groups=holdout[
                grouping_column
            ],
        )
    )

    validation = (
        holdout.iloc[
            validation_indices
        ]
        .copy()
    )

    test = (
        holdout.iloc[
            test_indices
        ]
        .copy()
    )

    return (
        train,
        validation,
        test,
        split_strategy,
    )


# ==================================================
# Preprocessing
# ==================================================

def build_preprocessor(
) -> ColumnTransformer:
    return ColumnTransformer(
        transformers=[
            (
                "categorical",
                OneHotEncoder(
                    handle_unknown="ignore",
                    sparse_output=False,
                ),
                CATEGORICAL_COLUMNS,
            ),
            (
                "numeric",
                "passthrough",
                NUMERIC_COLUMNS,
            ),
        ],
        remainder="drop",
        verbose_feature_names_out=False,
    )


# ==================================================
# Model construction
# ==================================================

def build_model(
        model_name: str,
        config: dict[str, object],
        random_state: int,
) -> Pipeline:
    if model_name == "Random Forest":
        estimator = (
            RandomForestRegressor(
                **config,
                n_jobs=-1,
                random_state=random_state,
            )
        )

    elif model_name == "XGBoost":
        estimator = (
            XGBRegressor(
                **config,
                objective="reg:squarederror",
                n_jobs=-1,
                random_state=random_state,
                tree_method="hist",
            )
        )

    else:
        raise ValueError(
            f"Unsupported model: "
            f"{model_name}"
        )

    return Pipeline(
        steps=[
            (
                "preprocessor",
                build_preprocessor(),
            ),
            (
                "model",
                estimator,
            ),
        ]
    )


# ==================================================
# Prediction metrics
# ==================================================

def calculate_metrics(
        actual: pd.Series,
        predicted: np.ndarray,
) -> dict[str, float]:
    actual_values = (
        actual.to_numpy(
            dtype=float
        )
    )

    absolute_errors = np.abs(
        actual_values
        - predicted
    )

    return {
        "mae_ms": float(
            mean_absolute_error(
                actual_values,
                predicted,
            )
        ),
        "rmse_ms": float(
            math.sqrt(
                mean_squared_error(
                    actual_values,
                    predicted,
                )
            )
        ),
        "r2": float(
            r2_score(
                actual_values,
                predicted,
            )
        ),
        "median_absolute_error_ms": float(
            np.median(
                absolute_errors
            )
        ),
        "p95_absolute_error_ms": float(
            np.percentile(
                absolute_errors,
                95,
            )
        ),
        "p99_absolute_error_ms": float(
            np.percentile(
                absolute_errors,
                99,
            )
        ),
    }


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
# Hyperparameter tuning
# ==================================================

def tune_model(
        model_name: str,
        configurations: list[
            dict[str, object]
        ],
        train: pd.DataFrame,
        validation: pd.DataFrame,
        random_state: int,
) -> tuple[
    dict[str, object],
    dict[str, float],
    pd.DataFrame,
]:
    tuning_rows: list[
        dict[str, object]
    ] = []

    best_config: (
            dict[str, object]
            | None
    ) = None

    best_metrics: (
            dict[str, float]
            | None
    ) = None

    best_mae = float(
        "inf"
    )

    for (
            config_index,
            config,
    ) in enumerate(
        configurations,
        start=1,
    ):
        print()
        print(
            "------------------------------------------"
        )
        print(
            f"{model_name} configuration "
            f"{config_index}/"
            f"{len(configurations)}"
        )
        print(
            "------------------------------------------"
        )

        print(
            json.dumps(
                config,
                indent=2,
            )
        )

        model = build_model(
            model_name,
            config,
            random_state,
        )

        model.fit(
            train[
                FEATURE_COLUMNS
            ],
            train[
                TARGET_COLUMN
            ],
        )

        predictions = model.predict(
            validation[
                FEATURE_COLUMNS
            ]
        )

        metrics = calculate_metrics(
            validation[
                TARGET_COLUMN
            ],
            predictions,
        )

        print(
            "\nValidation metrics"
        )

        print_metrics(
            metrics
        )

        tuning_rows.append(
            {
                "model": (
                    model_name
                ),
                "configuration_id": (
                    config_index
                ),
                **config,
                **metrics,
            }
        )

        if (
                metrics[
                    "mae_ms"
                ]
                < best_mae
        ):
            best_mae = (
                metrics[
                    "mae_ms"
                ]
            )

            best_config = (
                dict(
                    config
                )
            )

            best_metrics = (
                dict(
                    metrics
                )
            )

    if (
            best_config is None
            or best_metrics is None
    ):
        raise RuntimeError(
            "No valid configuration "
            f"found for {model_name}"
        )

    tuning_results = pd.DataFrame(
        tuning_rows
    )

    print()
    print(
        "=========================================="
    )
    print(
        f"Selected {model_name} configuration"
    )
    print(
        "=========================================="
    )

    print(
        json.dumps(
            best_config,
            indent=2,
        )
    )

    print(
        f"Validation MAE: "
        f"{best_metrics['mae_ms']:.2f} ms"
    )

    return (
        best_config,
        best_metrics,
        tuning_results,
    )


# ==================================================
# Final selected-model fitting
# ==================================================

def fit_selected_model(
        model_name: str,
        config: dict[str, object],
        train: pd.DataFrame,
        validation: pd.DataFrame,
        random_state: int,
) -> Pipeline:
    development_data = pd.concat(
        [
            train,
            validation,
        ],
        ignore_index=True,
    )

    model = build_model(
        model_name,
        config,
        random_state,
    )

    model.fit(
        development_data[
            FEATURE_COLUMNS
        ],
        development_data[
            TARGET_COLUMN
        ],
    )

    return model


# ==================================================
# Final held-out evaluation
# ==================================================

def evaluate_final_model(
        model_name: str,
        model: Pipeline,
        test: pd.DataFrame,
        output_directory: Path,
) -> dict[str, float]:
    predictions = model.predict(
        test[
            FEATURE_COLUMNS
        ]
    )

    metrics = calculate_metrics(
        test[
            TARGET_COLUMN
        ],
        predictions,
    )

    print()
    print(
        "=========================================="
    )
    print(
        f"{model_name} — held-out test"
    )
    print(
        "=========================================="
    )

    print_metrics(
        metrics
    )

    output = test[
        TRACEABILITY_COLUMNS
    ].copy()

    output[
        "predicted_waiting_time_ms"
    ] = predictions

    output[
        "absolute_error_ms"
    ] = np.abs(
        output[
            TARGET_COLUMN
        ]
        - output[
            "predicted_waiting_time_ms"
        ]
    )

    safe_name = (
        model_name
        .lower()
        .replace(
            " ",
            "-",
        )
    )

    output_path = (
            output_directory
            / f"{safe_name}-test-predictions.csv"
    )

    output.to_csv(
        output_path,
        index=False,
    )

    print(
        f"Test predictions written to: "
        f"{output_path}"
    )

    return metrics


# ==================================================
# Feature importance
# ==================================================

def save_feature_importance(
        model_name: str,
        model: Pipeline,
        output_directory: Path,
) -> None:
    preprocessor = (
        model.named_steps[
            "preprocessor"
        ]
    )

    estimator = (
        model.named_steps[
            "model"
        ]
    )

    feature_names = (
        preprocessor
        .get_feature_names_out()
    )

    importances = (
        estimator
        .feature_importances_
    )

    importance = pd.DataFrame(
        {
            "feature": (
                feature_names
            ),
            "importance": (
                importances
            ),
        }
    ).sort_values(
        "importance",
        ascending=False,
    )

    safe_name = (
        model_name
        .lower()
        .replace(
            " ",
            "-",
        )
    )

    output_path = (
            output_directory
            / (
                f"{safe_name}"
                "-feature-importance.csv"
            )
    )

    importance.to_csv(
        output_path,
        index=False,
    )

    print(
        f"Feature importance written to: "
        f"{output_path}"
    )


# ==================================================
# Java inference schema
# ==================================================

def save_model_schema(
        model: Pipeline,
        output_directory: Path,
) -> None:
    preprocessor = (
        model.named_steps[
            "preprocessor"
        ]
    )

    encoder = (
        preprocessor
        .named_transformers_[
            "categorical"
        ]
    )

    schema = {
        "target": (
            TARGET_COLUMN
        ),
        "categorical_columns": (
            CATEGORICAL_COLUMNS
        ),
        "numeric_columns": (
            NUMERIC_COLUMNS
        ),
        "categorical_values_in_training_order": {
            column: [
                str(
                    value
                )
                for value in values
            ]
            for (
                column,
                values,
            ) in zip(
                CATEGORICAL_COLUMNS,
                encoder.categories_,
            )
        },
        "transformed_feature_names": (
            preprocessor
            .get_feature_names_out()
            .tolist()
        ),
        "java_inference_note": (
            "Create one candidate feature vector for each "
            "processing queue using that queue's "
            "candidate-relative state features, predict one "
            "waiting time per candidate, and select the queue "
            "with the smallest prediction."
        ),
    }

    schema_path = (
            output_directory
            / "model-schema.json"
    )

    with schema_path.open(
            "w",
            encoding="utf-8",
    ) as file:
        json.dump(
            schema,
            file,
            indent=2,
        )

    print(
        f"Model schema written to: "
        f"{schema_path}"
    )


# ==================================================
# Final RF vs XGBoost report
# ==================================================

def save_model_comparison(
        metrics: dict[str, object],
        output_directory: Path,
) -> None:
    rows: list[
        dict[str, object]
    ] = []

    for (
            model_name,
            model_metrics,
    ) in metrics[
        "models"
    ].items():
        rows.append(
            {
                "model": (
                    model_name
                ),
                **model_metrics[
                    "test"
                ],
            }
        )

    comparison = pd.DataFrame(
        rows
    ).sort_values(
        by="mae_ms"
    )

    comparison_path = (
            output_directory
            / "model-comparison.csv"
    )

    comparison.to_csv(
        comparison_path,
        index=False,
    )

    print()
    print(
        "Held-out test model comparison"
    )
    print(
        "------------------------------"
    )

    print(
        comparison.to_string(
            index=False,
            float_format=(
                lambda value:
                f"{value:.4f}"
            ),
        )
    )

    best_test_model = (
        comparison.loc[
            comparison[
                "mae_ms"
            ].idxmin(),
            "model",
        ]
    )

    summary = (
        "Final predictive-model comparison\n"
        "=================================\n"
        f"Split strategy: "
        f"{metrics['split_strategy']}\n"
        f"Training runs: "
        f"{len(metrics['training_runs'])}\n"
        f"Validation runs: "
        f"{len(metrics['validation_runs'])}\n"
        f"Testing runs: "
        f"{len(metrics['testing_runs'])}\n"
        f"Lowest held-out test MAE: "
        f"{best_test_model}\n\n"
        "Hyperparameter configurations were selected "
        "using validation MAE. The selected "
        "configuration for each model was then refitted "
        "using the combined training and validation "
        "partitions and assessed once on the held-out "
        "test partition.\n"
    )

    summary_path = (
            output_directory
            / "evaluation-summary.txt"
    )

    summary_path.write_text(
        summary,
        encoding="utf-8",
    )

    print(
        f"\nModel comparison written to: "
        f"{comparison_path}"
    )

    print(
        f"Evaluation summary written to: "
        f"{summary_path}"
    )


# ==================================================
# Main
# ==================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Tune, train and evaluate Random Forest "
            "and XGBoost regressors to predict "
            "realised waiting time."
        )
    )

    parser.add_argument(
        "data_directory",
        type=Path,
        help=(
            "Directory containing run folders "
            "with training-dataset.csv"
        ),
    )

    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path(
            "scripts/ml-training/output"
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

    data = load_datasets(
        data_directory
    )

    validate_dataset(
        data
    )

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
        f"Split strategy : "
        f"{split_strategy}"
    )

    print(
        f"Training rows  : "
        f"{len(train):,}"
    )

    print(
        f"Validation rows: "
        f"{len(validation):,}"
    )

    print(
        f"Testing rows   : "
        f"{len(test):,}"
    )

    print(
        f"Training runs  : "
        f"{train['run_id'].nunique()}"
    )

    print(
        f"Validation runs: "
        f"{validation['run_id'].nunique()}"
    )

    print(
        f"Testing runs   : "
        f"{test['run_id'].nunique()}"
    )

    model_configurations = {
        "Random Forest": (
            RANDOM_FOREST_CONFIGS
        ),
        "XGBoost": (
            XGBOOST_CONFIGS
        ),
    }

    selected_models: dict[
        str,
        Pipeline,
    ] = {}

    tuning_frames: list[
        pd.DataFrame
    ] = []

    all_metrics: dict[
        str,
        object,
    ] = {
        "target": (
            TARGET_COLUMN
        ),
        "selection_metric": (
            "validation_mae_ms"
        ),
        "split_strategy": (
            split_strategy
        ),
        "random_state": (
            args.random_state
        ),
        "training_runs": sorted(
            train[
                "run_id"
            ]
            .unique()
            .tolist()
        ),
        "validation_runs": sorted(
            validation[
                "run_id"
            ]
            .unique()
            .tolist()
        ),
        "testing_runs": sorted(
            test[
                "run_id"
            ]
            .unique()
            .tolist()
        ),
        "models": {},
    }

    for (
            model_name,
            configurations,
    ) in model_configurations.items():

        print()
        print(
            "=========================================="
        )
        print(
            f"Tuning {model_name}"
        )
        print(
            "=========================================="
        )

        (
            best_config,
            best_validation_metrics,
            tuning_results,
        ) = tune_model(
            model_name,
            configurations,
            train,
            validation,
            args.random_state,
        )

        tuning_frames.append(
            tuning_results
        )

        print()
        print(
            f"Refitting selected {model_name} "
            "configuration using training "
            "+ validation data..."
        )

        selected_model = (
            fit_selected_model(
                model_name,
                best_config,
                train,
                validation,
                args.random_state,
            )
        )

        selected_models[
            model_name
        ] = selected_model

        test_metrics = (
            evaluate_final_model(
                model_name,
                selected_model,
                test,
                output_directory,
            )
        )

        safe_name = (
            model_name
            .lower()
            .replace(
                " ",
                "-",
            )
        )

        pipeline_path = (
                output_directory
                / (
                    f"{safe_name}"
                    "-pipeline.joblib"
                )
        )

        joblib.dump(
            selected_model,
            pipeline_path,
        )

        print(
            f"Pipeline written to: "
            f"{pipeline_path}"
        )

        save_feature_importance(
            model_name,
            selected_model,
            output_directory,
        )

        all_metrics[
            "models"
        ][model_name] = {
            "selected_configuration": (
                best_config
            ),
            "validation": (
                best_validation_metrics
            ),
            "test": (
                test_metrics
            ),
        }

    # --------------------------------------------------
    # Save full tuning results
    # --------------------------------------------------

    tuning_results = pd.concat(
        tuning_frames,
        ignore_index=True,
    )

    tuning_path = (
            output_directory
            / (
                "hyperparameter-"
                "tuning-results.csv"
            )
    )

    tuning_results.to_csv(
        tuning_path,
        index=False,
    )

    print()
    print(
        "Hyperparameter tuning results "
        f"written to: {tuning_path}"
    )

    # --------------------------------------------------
    # Export selected XGBoost runtime model
    # --------------------------------------------------

    xgboost_model_path = (
            output_directory
            / "xgboost-model.json"
    )

    selected_models[
        "XGBoost"
    ].named_steps[
        "model"
    ].save_model(
        xgboost_model_path
    )

    print(
        f"XGBoost model written to: "
        f"{xgboost_model_path}"
    )

    save_model_schema(
        selected_models[
            "XGBoost"
        ],
        output_directory,
    )

    # --------------------------------------------------
    # Save model metrics
    # --------------------------------------------------

    metrics_path = (
            output_directory
            / "model-metrics.json"
    )

    with metrics_path.open(
            "w",
            encoding="utf-8",
    ) as file:
        json.dump(
            all_metrics,
            file,
            indent=2,
        )

    print(
        f"Model metrics written to: "
        f"{metrics_path}"
    )

    # --------------------------------------------------
    # Save final RF vs XGBoost comparison
    # --------------------------------------------------

    save_model_comparison(
        all_metrics,
        output_directory,
    )

    print()
    print(
        "=========================================="
    )
    print(
        "Training completed successfully"
    )
    print(
        "=========================================="
    )
    print(
        "Hyperparameter selection metric: "
        "validation MAE"
    )
    print(
        f"Outputs: "
        f"{output_directory}"
    )
    print(
        "Selected XGBoost model exported for "
        "runtime inference."
    )
    print(
        "=========================================="
    )


if __name__ == "__main__":
    main()