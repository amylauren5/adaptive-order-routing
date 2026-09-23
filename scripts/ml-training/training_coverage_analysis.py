#!/usr/bin/env python3
"""
Training-coverage diagnostic for Chapter 5.

Purpose
-------
Compare periodically sampled queue-state measurements from the 25
model-development runs with the same measurements from the final ML
evaluation runs.

This diagnostic assesses whether the final evaluation workloads entered
queue-state regions that were weakly represented during model development.
The comparison uses like-for-like queue_metrics.csv measurements because
equivalent candidate-level feature observations were not recorded for all
candidate queues during final evaluation. It therefore assesses queue-state
coverage rather than providing a direct comparison of candidate-level
XGBoost input rows.

The comparison is limited to:
    - queue length
    - arrival rate
    - consumer throughput
    - utilisation
    - backlog growth

Only the active workload window is analysed, excluding post-workload
queue draining.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd


FEATURES = {
    "queue_length": "Queue length",
    "arrival_rate": "Arrival rate",
    "consumer_throughput": "Consumer throughput",
    "utilisation": "Utilisation",
    "backlog_growth": "Backlog growth",
}

CONDITION_ORDER = [
    "Training",
    "Steady",
    "Strong-short",
    "Moderate-long",
]


def parse_args() -> argparse.Namespace:
    script_dir = Path(__file__).resolve().parent

    parser = argparse.ArgumentParser(
        description="Compare training and final ML queue-state coverage."
    )
    parser.add_argument(
        "--data-root",
        type=Path,
        default=(script_dir / "../../data").resolve(),
        help="Project data directory containing training/ and ml/ (default: ../../data).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=(script_dir / "training-coverage-output").resolve(),
        help="Directory for generated CSV files.",
    )
    return parser.parse_args()


def load_metadata(run_dir: Path) -> dict:
    metadata_path = run_dir / "run_metadata.json"
    if not metadata_path.exists():
        raise FileNotFoundError(f"Missing metadata file: {metadata_path}")

    with metadata_path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def classify_evaluation_condition(metadata: dict) -> str:
    """Map final ML run metadata to the three dissertation workload conditions."""
    burst_enabled = bool(metadata["burst_enabled"])

    if not burst_enabled:
        return "Steady"

    multiplier = float(metadata["burst_multiplier"])
    duration = int(metadata["burst_duration_seconds"])

    if np.isclose(multiplier, 0.25) and duration == 5:
        return "Strong-short"

    if np.isclose(multiplier, 0.50) and duration == 10:
        return "Moderate-long"

    raise ValueError(
        "Unrecognised final-evaluation burst condition in "
        f"{metadata.get('run_id', '<unknown>')}: "
        f"multiplier={multiplier}, duration={duration}"
    )


def queue_metrics_to_long(run_dir: Path, condition: str) -> pd.DataFrame:
    """
    Load one run's queue_metrics.csv and convert q1/q2/q3 measurements to
    one row per queue observation.

    Only observations within the workload publication window are retained.
    """
    metrics_path = run_dir / "queue_metrics.csv"
    if not metrics_path.exists():
        raise FileNotFoundError(f"Missing queue metrics file: {metrics_path}")

    metadata = load_metadata(run_dir)
    df = pd.read_csv(metrics_path)

    required_base = {"run_id", "elapsed_ms"}
    missing_base = required_base - set(df.columns)
    if missing_base:
        raise ValueError(
            f"{metrics_path} is missing required columns: {sorted(missing_base)}"
        )

    duration_ms = int(metadata["workload_duration_seconds"]) * 1000

    # Routing decisions occur during workload publication. Excluding the
    # post-workload drain gives a closer diagnostic of states that could
    # actually matter to the routing model.
    df = df.loc[(df["elapsed_ms"] >= 0) & (df["elapsed_ms"] <= duration_ms)].copy()

    frames = []
    for queue in ("q1", "q2", "q3"):
        rename_map = {
            f"{queue}_length": "queue_length",
            f"{queue}_arrival_rate": "arrival_rate",
            f"{queue}_consumer_throughput": "consumer_throughput",
            f"{queue}_utilisation": "utilisation",
            f"{queue}_backlog_growth": "backlog_growth",
        }

        missing = set(rename_map) - set(df.columns)
        if missing:
            raise ValueError(
                f"{metrics_path} is missing queue-state columns: {sorted(missing)}"
            )

        part = df[
            ["run_id", "elapsed_ms", *rename_map.keys()]
        ].rename(columns=rename_map)

        part.insert(2, "queue", queue)
        part["condition"] = condition
        frames.append(part)

    long_df = pd.concat(frames, ignore_index=True)

    for feature in FEATURES:
        long_df[feature] = pd.to_numeric(long_df[feature], errors="coerce")

    return long_df


def load_training(data_root: Path) -> pd.DataFrame:
    training_root = data_root / "training"
    if not training_root.exists():
        raise FileNotFoundError(f"Training directory not found: {training_root}")

    run_dirs = sorted(
        path for path in training_root.iterdir()
        if path.is_dir() and (path / "queue_metrics.csv").exists()
    )

    if not run_dirs:
        raise RuntimeError(f"No training runs found under {training_root}")

    frames = [queue_metrics_to_long(run_dir, "Training") for run_dir in run_dirs]
    result = pd.concat(frames, ignore_index=True)

    print(f"Loaded {len(run_dirs)} training runs "
          f"({len(result):,} per-queue periodic observations).")
    return result


def load_final_ml_evaluation(data_root: Path) -> pd.DataFrame:
    ml_root = data_root / "ml"
    if not ml_root.exists():
        raise FileNotFoundError(f"ML evaluation directory not found: {ml_root}")

    run_dirs = sorted(
        path for path in ml_root.iterdir()
        if path.is_dir() and (path / "queue_metrics.csv").exists()
    )

    if not run_dirs:
        raise RuntimeError(f"No final ML runs found under {ml_root}")

    frames = []
    counts = {}

    for run_dir in run_dirs:
        metadata = load_metadata(run_dir)

        # Protect against accidentally including non-final or non-ML runs.
        if metadata.get("strategy") != "ml":
            continue

        condition = classify_evaluation_condition(metadata)
        frames.append(queue_metrics_to_long(run_dir, condition))
        counts[condition] = counts.get(condition, 0) + 1

    if not frames:
        raise RuntimeError(f"No ML evaluation runs could be loaded from {ml_root}")

    expected = {"Steady": 5, "Strong-short": 5, "Moderate-long": 5}
    if counts != expected:
        raise RuntimeError(
            "Expected five final ML runs per condition, but found "
            f"{counts}. Check the data directory."
        )

    result = pd.concat(frames, ignore_index=True)

    print(
        "Loaded final ML evaluation runs: "
        + ", ".join(f"{condition}={counts[condition]}" for condition in expected)
        + f" ({len(result):,} per-queue periodic observations)."
    )
    return result


def finite_values(df: pd.DataFrame, feature: str) -> np.ndarray:
    values = df[feature].to_numpy(dtype=float)
    return values[np.isfinite(values)]


def build_distribution_summary(all_data: pd.DataFrame) -> pd.DataFrame:
    rows = []

    for condition in CONDITION_ORDER:
        condition_df = all_data.loc[all_data["condition"] == condition]

        for feature, label in FEATURES.items():
            values = finite_values(condition_df, feature)
            if values.size == 0:
                raise RuntimeError(
                    f"No finite values for {feature} in condition {condition}"
                )

            rows.append(
                {
                    "condition": condition,
                    "feature": feature,
                    "feature_label": label,
                    "n": int(values.size),
                    "median": float(np.median(values)),
                    "q1": float(np.quantile(values, 0.25)),
                    "q3": float(np.quantile(values, 0.75)),
                    "p95": float(np.quantile(values, 0.95)),
                    "p99": float(np.quantile(values, 0.99)),
                    "maximum": float(np.max(values)),
                }
            )

    return pd.DataFrame(rows)


def build_exceedance_summary(
    training: pd.DataFrame,
    evaluation: pd.DataFrame,
) -> pd.DataFrame:
    rows = []

    for feature, label in FEATURES.items():
        train_values = finite_values(training, feature)
        thresholds = {
            "training_p95": float(np.quantile(train_values, 0.95)),
            "training_p99": float(np.quantile(train_values, 0.99)),
            "training_maximum": float(np.max(train_values)),
        }

        for condition in CONDITION_ORDER[1:]:
            eval_values = finite_values(
                evaluation.loc[evaluation["condition"] == condition],
                feature,
            )

            rows.append(
                {
                    "condition": condition,
                    "feature": feature,
                    "feature_label": label,
                    **thresholds,
                    "evaluation_n": int(eval_values.size),
                    "pct_above_training_p95": float(
                        100.0 * np.mean(eval_values > thresholds["training_p95"])
                    ),
                    "pct_above_training_p99": float(
                        100.0 * np.mean(eval_values > thresholds["training_p99"])
                    ),
                    "pct_above_training_maximum": float(
                        100.0 * np.mean(eval_values > thresholds["training_maximum"])
                    ),
                }
            )

    return pd.DataFrame(rows)


def build_compact_table(
    distribution: pd.DataFrame,
    exceedance: pd.DataFrame,
) -> pd.DataFrame:
    """
    Produce a compact table suitable for deciding what to report in Chapter 5.
    It is intentionally descriptive, not inferential.
    """
    train = (
        distribution.loc[distribution["condition"] == "Training"]
        .set_index("feature")
    )

    rows = []

    for feature, label in FEATURES.items():
        row = {
            "feature": label,
            "training_p99": train.loc[feature, "p99"],
            "training_max": train.loc[feature, "maximum"],
        }

        for condition, prefix in [
            ("Steady", "steady"),
            ("Strong-short", "strong_short"),
            ("Moderate-long", "moderate_long"),
        ]:
            condition_dist = distribution.loc[
                (distribution["condition"] == condition)
                & (distribution["feature"] == feature)
            ].iloc[0]

            condition_exc = exceedance.loc[
                (exceedance["condition"] == condition)
                & (exceedance["feature"] == feature)
            ].iloc[0]

            row[f"{prefix}_p99"] = condition_dist["p99"]
            row[f"{prefix}_max"] = condition_dist["maximum"]
            row[f"{prefix}_pct_above_training_max"] = condition_exc[
                "pct_above_training_maximum"
            ]

        rows.append(row)

    return pd.DataFrame(rows)


def main() -> None:
    args = parse_args()
    data_root = args.data_root.resolve()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Data root: {data_root}")
    print(f"Output directory: {output_dir}")

    training = load_training(data_root)
    evaluation = load_final_ml_evaluation(data_root)

    all_data = pd.concat([training, evaluation], ignore_index=True)

    distribution = build_distribution_summary(all_data)
    exceedance = build_exceedance_summary(training, evaluation)
    compact = build_compact_table(distribution, exceedance)

    distribution_path = output_dir / "training_coverage_summary.csv"
    exceedance_path = output_dir / "training_coverage_exceedance.csv"
    compact_path = output_dir / "training_coverage_table.csv"

    distribution.to_csv(distribution_path, index=False, float_format="%.6f")
    exceedance.to_csv(exceedance_path, index=False, float_format="%.6f")
    compact.to_csv(compact_path, index=False, float_format="%.6f")

    print("\nCoverage diagnostic complete.")
    print(f"  {distribution_path}")
    print(f"  {exceedance_path}")
    print(f"  {compact_path}")

    print("\nCompact diagnostic table:")
    with pd.option_context(
        "display.max_columns", None,
        "display.width", 220,
        "display.float_format", lambda x: f"{x:.3f}",
    ):
        print(compact.to_string(index=False))


if __name__ == "__main__":
    main()
