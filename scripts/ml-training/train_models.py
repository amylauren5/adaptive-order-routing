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
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from xgboost import XGBRegressor


TARGET_COLUMN = "realised_waiting_time_ms"

CATEGORICAL_COLUMNS = [
    "order_status",
    "category"
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

FEATURE_COLUMNS = CATEGORICAL_COLUMNS + NUMERIC_COLUMNS

TRACEABILITY_COLUMNS = [
    "run_id",
    "routing_decision_id",
    "order_id",
    "order_status",
    "selected_queue",
    TARGET_COLUMN,
]


def load_datasets(data_directory: Path) -> pd.DataFrame:
    dataset_paths = sorted(data_directory.glob("*/training-dataset.csv"))

    if not dataset_paths:
        raise FileNotFoundError(
            f"No training-dataset.csv files found under {data_directory}"
        )

    frames: list[pd.DataFrame] = []

    for dataset_path in dataset_paths:
        frame = pd.read_csv(dataset_path)
        frame["run_id"] = dataset_path.parent.name
        frames.append(frame)

        print(
            f"Loaded {len(frame):,} rows from '{dataset_path.parent.name}'"
        )

    combined = pd.concat(frames, ignore_index=True)

    print(
        f"Combined dataset: {len(combined):,} rows from "
        f"{combined['run_id'].nunique()} run(s)"
    )

    return combined


def validate_dataset(data: pd.DataFrame) -> None:
    required_columns = {
        "run_id",
        "routing_decision_id",
        "order_id",
        "selected_queue",
        TARGET_COLUMN,
        *FEATURE_COLUMNS,
    }

    missing_columns = sorted(required_columns - set(data.columns))

    if missing_columns:
        raise ValueError(
            f"Dataset is missing required columns: {missing_columns}"
        )

    duplicate_count = int(data["routing_decision_id"].duplicated().sum())

    if duplicate_count:
        raise ValueError(
            f"Dataset contains {duplicate_count} duplicate routing decision IDs"
        )

    if data[TARGET_COLUMN].isna().any():
        raise ValueError(f"{TARGET_COLUMN} contains missing values")

    if (data[TARGET_COLUMN] < 0).any():
        raise ValueError(f"{TARGET_COLUMN} contains negative values")

    missing_features = data[FEATURE_COLUMNS].isna().sum()
    missing_features = missing_features[missing_features > 0]

    if not missing_features.empty:
        raise ValueError(
            "Feature columns contain missing values:\n"
            f"{missing_features.to_string()}"
        )

    numeric_values = data[NUMERIC_COLUMNS].to_numpy(dtype=float)

    if not np.isfinite(numeric_values).all():
        raise ValueError("Numeric features contain non-finite values")


def split_dataset(
        data: pd.DataFrame,
        validation_size: float,
        test_size: float,
        random_state: int,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, str]:
    if validation_size <= 0 or test_size <= 0:
        raise ValueError("Validation and test sizes must be positive")

    if validation_size + test_size >= 1:
        raise ValueError(
            "Validation size plus test size must be less than 1"
        )

    run_count = int(data["run_id"].nunique())

    if run_count >= 3:
        grouping_column = "run_id"
        split_strategy = "complete experiment runs"
    else:
        grouping_column = "order_id"
        split_strategy = "complete orders (development fallback only)"

        print(
            "\nWARNING: Fewer than three complete runs were found.\n"
            "The script will split by complete order_id groups only to verify "
            "that the ML pipeline works.\n"
            "Do not report these scores as final thesis results.\n"
        )

    groups = data[grouping_column]
    holdout_size = validation_size + test_size

    first_split = GroupShuffleSplit(
        n_splits=1,
        test_size=holdout_size,
        random_state=random_state,
    )

    train_indices, holdout_indices = next(
        first_split.split(data, groups=groups)
    )

    train = data.iloc[train_indices].copy()
    holdout = data.iloc[holdout_indices].copy()

    relative_test_size = test_size / holdout_size

    second_split = GroupShuffleSplit(
        n_splits=1,
        test_size=relative_test_size,
        random_state=random_state + 1,
    )

    validation_indices, test_indices = next(
        second_split.split(
            holdout,
            groups=holdout[grouping_column],
        )
    )

    validation = holdout.iloc[validation_indices].copy()
    test = holdout.iloc[test_indices].copy()

    return train, validation, test, split_strategy


def build_preprocessor() -> ColumnTransformer:
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


def build_models(random_state: int) -> dict[str, Pipeline]:
    return {
        "Random Forest": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                (
                    "model",
                    RandomForestRegressor(
                        n_estimators=400,
                        min_samples_leaf=2,
                        max_features="sqrt",
                        n_jobs=-1,
                        random_state=random_state,
                    ),
                ),
            ]
        ),
        "XGBoost": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                (
                    "model",
                    XGBRegressor(
                        objective="reg:squarederror",
                        n_estimators=500,
                        learning_rate=0.05,
                        max_depth=6,
                        min_child_weight=2,
                        subsample=0.8,
                        colsample_bytree=0.8,
                        reg_lambda=1.0,
                        n_jobs=-1,
                        random_state=random_state,
                        tree_method="hist",
                    ),
                ),
            ]
        ),
    }


def calculate_metrics(
        actual: pd.Series,
        predicted: np.ndarray,
) -> dict[str, float]:
    actual_values = actual.to_numpy(dtype=float)
    absolute_errors = np.abs(actual_values - predicted)

    return {
        "mae_ms": float(mean_absolute_error(actual_values, predicted)),
        "rmse_ms": float(
            math.sqrt(mean_squared_error(actual_values, predicted))
        ),
        "r2": float(r2_score(actual_values, predicted)),
        "median_absolute_error_ms": float(np.median(absolute_errors)),
        "p95_absolute_error_ms": float(np.percentile(absolute_errors, 95)),
        "p99_absolute_error_ms": float(np.percentile(absolute_errors, 99)),
    }


def evaluate_model(
        model_name: str,
        model: Pipeline,
        data: pd.DataFrame,
        split_name: str,
        output_directory: Path,
) -> dict[str, float]:
    predictions = model.predict(data[FEATURE_COLUMNS])
    metrics = calculate_metrics(data[TARGET_COLUMN], predictions)

    print(f"\n{model_name} — {split_name}")
    print("-" * (len(model_name) + len(split_name) + 3))
    print(f"MAE               : {metrics['mae_ms']:.2f} ms")
    print(f"RMSE              : {metrics['rmse_ms']:.2f} ms")
    print(f"R²                : {metrics['r2']:.4f}")
    print(
        f"P95 abs. error    : "
        f"{metrics['p95_absolute_error_ms']:.2f} ms"
    )
    print(
        f"P99 abs. error    : "
        f"{metrics['p99_absolute_error_ms']:.2f} ms"
    )

    output = data[TRACEABILITY_COLUMNS].copy()
    output["predicted_waiting_time_ms"] = predictions
    output["absolute_error_ms"] = np.abs(
        output[TARGET_COLUMN] - output["predicted_waiting_time_ms"]
    )

    safe_name = model_name.lower().replace(" ", "-")
    output.to_csv(
        output_directory / f"{safe_name}-{split_name}-predictions.csv",
        index=False,
        )

    return metrics


def save_feature_importance(
        model_name: str,
        model: Pipeline,
        output_directory: Path,
) -> None:
    preprocessor = model.named_steps["preprocessor"]
    estimator = model.named_steps["model"]

    feature_names = preprocessor.get_feature_names_out()
    importances = estimator.feature_importances_

    importance = pd.DataFrame(
        {
            "feature": feature_names,
            "importance": importances,
        }
    ).sort_values(
        "importance",
        ascending=False,
    )

    safe_name = model_name.lower().replace(" ", "-")
    output_path = output_directory / f"{safe_name}-feature-importance.csv"

    importance.to_csv(output_path, index=False)
    print(f"Feature importance written to: {output_path}")


def save_model_schema(
        model: Pipeline,
        output_directory: Path,
) -> None:
    preprocessor = model.named_steps["preprocessor"]
    encoder = preprocessor.named_transformers_["categorical"]

    schema = {
        "target": TARGET_COLUMN,
        "categorical_columns": CATEGORICAL_COLUMNS,
        "numeric_columns": NUMERIC_COLUMNS,
        "categorical_values_in_training_order": {
            column: [str(value) for value in values]
            for column, values in zip(
                CATEGORICAL_COLUMNS,
                encoder.categories_,
            )
        },
        "transformed_feature_names": (
            preprocessor.get_feature_names_out().tolist()
        ),
        "java_inference_note": (
            "Create one candidate feature vector for each processing queue "
            "using that queue's candidate-relative state features, predict "
            "one waiting time per candidate, and select the queue with the "
            "smallest prediction."
        ),
    }

    with (output_directory / "model-schema.json").open(
            "w",
            encoding="utf-8",
    ) as file:
        json.dump(schema, file, indent=2)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Train Random Forest and XGBoost regressors to predict "
            "realised waiting time."
        )
    )
    parser.add_argument(
        "data_directory",
        type=Path,
        help="Directory containing run folders with training-dataset.csv",
    )
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path("scripts/ml-training/output"),
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

    data_directory = args.data_directory.resolve()
    output_directory = args.output_directory.resolve()

    if not data_directory.is_dir():
        raise NotADirectoryError(
            f"Invalid data directory: {data_directory}"
        )

    output_directory.mkdir(parents=True, exist_ok=True)

    data = load_datasets(data_directory)
    validate_dataset(data)

    train, validation, test, split_strategy = split_dataset(
        data,
        validation_size=args.validation_size,
        test_size=args.test_size,
        random_state=args.random_state,
    )

    print(f"\nSplit strategy : {split_strategy}")
    print(f"Training rows  : {len(train):,}")
    print(f"Validation rows: {len(validation):,}")
    print(f"Testing rows   : {len(test):,}")

    models = build_models(args.random_state)

    all_metrics: dict[str, object] = {
        "target": TARGET_COLUMN,
        "split_strategy": split_strategy,
        "random_state": args.random_state,
        "training_runs": sorted(train["run_id"].unique().tolist()),
        "validation_runs": sorted(validation["run_id"].unique().tolist()),
        "testing_runs": sorted(test["run_id"].unique().tolist()),
        "models": {},
    }

    for model_name, model in models.items():
        print(f"\nTraining {model_name}...")

        model.fit(
            train[FEATURE_COLUMNS],
            train[TARGET_COLUMN],
        )

        validation_metrics = evaluate_model(
            model_name,
            model,
            validation,
            "validation",
            output_directory,
        )

        test_metrics = evaluate_model(
            model_name,
            model,
            test,
            "test",
            output_directory,
        )

        safe_name = model_name.lower().replace(" ", "-")

        joblib.dump(
            model,
            output_directory / f"{safe_name}-pipeline.joblib",
            )

        save_feature_importance(
            model_name,
            model,
            output_directory,
        )

        all_metrics["models"][model_name] = {
            "validation": validation_metrics,
            "test": test_metrics,
        }

    models["XGBoost"].named_steps["model"].save_model(
        output_directory / "xgboost-model.json"
    )

    save_model_schema(
        models["Random Forest"],
        output_directory,
    )

    with (output_directory / "model-metrics.json").open(
            "w",
            encoding="utf-8",
    ) as file:
        json.dump(all_metrics, file, indent=2)

    best_model = min(
        all_metrics["models"],
        key=lambda name: all_metrics["models"][name]["validation"]["mae_ms"],
    )

    print(
        "\n==================================================\n"
        "Training completed successfully\n"
        f"Best provisional validation MAE: {best_model}\n"
        f"Outputs: {output_directory}\n"
        "=================================================="
    )


if __name__ == "__main__":
    main()
