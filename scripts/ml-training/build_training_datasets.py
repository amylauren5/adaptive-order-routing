from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd


OBSERVATIONS_FILE = "pending-routing-observations.csv"
OUTCOMES_FILE = "routing-decision-outcomes.csv"
OUTPUT_FILE = "training-dataset.csv"

CANDIDATE_COLUMNS = [
    "candidate_queue_length",
    "candidate_arrival_rate",
    "candidate_consumer_throughput",
    "candidate_utilisation",
    "candidate_backlog_growth",
]


def validate_unique_ids(
        dataframe: pd.DataFrame,
        name: str,
) -> None:
    if "routing_decision_id" not in dataframe.columns:
        raise ValueError(
            f"{name} is missing routing_decision_id"
        )

    duplicate_count = (
        dataframe["routing_decision_id"]
        .duplicated()
        .sum()
    )

    if duplicate_count > 0:
        raise ValueError(
            f"{name} contains {duplicate_count} duplicate "
            "routing_decision_id values"
        )


def validate_candidate_features(
        dataframe: pd.DataFrame,
) -> None:
    if dataframe[CANDIDATE_COLUMNS].isna().any().any():
        raise ValueError(
            "Merged dataset contains missing candidate feature values"
        )

    feature_values = dataframe[
        CANDIDATE_COLUMNS
    ].to_numpy(dtype=float)

    if not np.isfinite(feature_values).all():
        raise ValueError(
            "Merged dataset contains non-finite candidate feature values"
        )


def build_training_dataset(
        run_directory: Path,
) -> Path:
    observations_path = (
            run_directory / OBSERVATIONS_FILE
    )
    outcomes_path = (
            run_directory / OUTCOMES_FILE
    )
    output_path = (
            run_directory / OUTPUT_FILE
    )

    if not observations_path.exists():
        raise FileNotFoundError(
            f"Missing observations file: {observations_path}"
        )

    if not outcomes_path.exists():
        raise FileNotFoundError(
            f"Missing outcomes file: {outcomes_path}"
        )

    observations = pd.read_csv(
        observations_path
    )

    outcomes = pd.read_csv(
        outcomes_path
    )

    validate_unique_ids(
        observations,
        OBSERVATIONS_FILE,
    )

    validate_unique_ids(
        outcomes,
        OUTCOMES_FILE,
    )

    merged = observations.merge(
        outcomes,
        on="routing_decision_id",
        how="outer",
        suffixes=(
            "_observation",
            "_outcome",
        ),
        indicator=True,
        validate="one_to_one",
    )

    missing_outcomes = merged[
        merged["_merge"] == "left_only"
        ]

    missing_observations = merged[
        merged["_merge"] == "right_only"
        ]

    if not missing_outcomes.empty:
        raise ValueError(
            f"{len(missing_outcomes)} observations "
            "have no matching outcome"
        )

    if not missing_observations.empty:
        raise ValueError(
            f"{len(missing_observations)} outcomes "
            "have no matching observation"
        )

    if (
            "selected_queue_observation" in merged.columns
            and "selected_queue_outcome" in merged.columns
    ):
        mismatches = merged[
            merged["selected_queue_observation"]
            != merged["selected_queue_outcome"]
            ]

        if not mismatches.empty:
            raise ValueError(
                f"{len(mismatches)} rows have "
                "inconsistent selected queues"
            )

        merged = (
            merged
            .drop(
                columns=[
                    "selected_queue_outcome"
                ]
            )
            .rename(
                columns={
                    "selected_queue_observation":
                        "selected_queue"
                }
            )
        )

    merged = merged.drop(
        columns=["_merge"]
    )

    merged = add_candidate_features(
        merged
    )

    validate_candidate_features(
        merged
    )

    if (
            "realised_waiting_time_ms"
            not in merged.columns
    ):
        raise ValueError(
            "Merged dataset is missing "
            "realised_waiting_time_ms"
        )

    if (
            merged["realised_waiting_time_ms"]
                    .isna()
                    .any()
    ):
        raise ValueError(
            "Merged dataset contains missing "
            "realised waiting times"
        )

    if (
            merged["realised_waiting_time_ms"] < 0
    ).any():
        raise ValueError(
            "Merged dataset contains negative "
            "realised waiting times"
        )

    if not np.isfinite(
            merged[
                "realised_waiting_time_ms"
            ].to_numpy(dtype=float)
    ).all():
        raise ValueError(
            "Merged dataset contains non-finite "
            "realised waiting times"
        )

    if output_path.exists():
        raise FileExistsError(
            f"Output already exists: {output_path}"
        )

    merged.insert(
        0,
        "run_id",
        run_directory.name,
    )

    merged.to_csv(
        output_path,
        index=False,
    )

    print(
        f"[OK] {run_directory.name}: "
        f"{len(observations)} observations, "
        f"{len(outcomes)} outcomes, "
        f"{len(merged)} joined rows"
    )

    print(
        f"     Written to: {output_path}"
    )

    return output_path


def add_candidate_features(
        data: pd.DataFrame,
) -> pd.DataFrame:
    queue_prefix = {
        "processing.queue-1": "queue1",
        "processing.queue-2": "queue2",
        "processing.queue-3": "queue3",
    }

    required_columns = {
        "selected_queue",
        "queue1_length",
        "queue1_arrival_rate",
        "queue1_consumer_throughput",
        "queue1_utilisation",
        "queue1_backlog_growth",
        "queue2_length",
        "queue2_arrival_rate",
        "queue2_consumer_throughput",
        "queue2_utilisation",
        "queue2_backlog_growth",
        "queue3_length",
        "queue3_arrival_rate",
        "queue3_consumer_throughput",
        "queue3_utilisation",
        "queue3_backlog_growth",
    }

    missing_columns = (
            required_columns
            - set(data.columns)
    )

    if missing_columns:
        raise ValueError(
            "Training observations are missing "
            "required columns: "
            + ", ".join(
                sorted(missing_columns)
            )
        )

    output = data.copy()

    for row_index, row in output.iterrows():
        selected_queue = (
            row["selected_queue"]
        )

        if selected_queue not in queue_prefix:
            raise ValueError(
                f"Unknown selected queue: "
                f"{selected_queue}"
            )

        prefix = queue_prefix[
            selected_queue
        ]

        output.at[
            row_index,
            "candidate_queue_length",
        ] = row[
            f"{prefix}_length"
        ]

        output.at[
            row_index,
            "candidate_arrival_rate",
        ] = row[
            f"{prefix}_arrival_rate"
        ]

        output.at[
            row_index,
            "candidate_consumer_throughput",
        ] = row[
            f"{prefix}_consumer_throughput"
        ]

        output.at[
            row_index,
            "candidate_utilisation",
        ] = row[
            f"{prefix}_utilisation"
        ]

        output.at[
            row_index,
            "candidate_backlog_growth",
        ] = row[
            f"{prefix}_backlog_growth"
        ]

    return output


def find_run_directories(
        data_directory: Path,
) -> list[Path]:
    return sorted(
        directory
        for directory
        in data_directory.iterdir()
        if directory.is_dir()
        and (
                directory
                / OBSERVATIONS_FILE
        ).exists()
        and (
                directory
                / OUTCOMES_FILE
        ).exists()
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Build one training dataset "
            "for each experiment run folder."
        )
    )

    parser.add_argument(
        "data_directory",
        type=Path,
        help=(
            "Directory containing "
            "experiment run folders"
        ),
    )

    parser.add_argument(
        "--run-id",
        help=(
            "Process only one "
            "experiment folder"
        ),
    )

    args = parser.parse_args()

    data_directory = (
        args.data_directory.resolve()
    )

    if not data_directory.is_dir():
        raise NotADirectoryError(
            f"Invalid data directory: "
            f"{data_directory}"
        )

    if args.run_id:
        run_directory = (
                data_directory
                / args.run_id
        )

        if not run_directory.is_dir():
            raise NotADirectoryError(
                f"Invalid run directory: "
                f"{run_directory}"
            )

        run_directories = [
            run_directory
        ]
    else:
        run_directories = (
            find_run_directories(
                data_directory
            )
        )

    if not run_directories:
        raise RuntimeError(
            "No experiment folders "
            "containing both CSV files "
            "were found"
        )

    failures = 0

    for run_directory in run_directories:
        try:
            build_training_dataset(
                run_directory
            )
        except Exception as exception:
            failures += 1

            print(
                f"[FAILED] "
                f"{run_directory.name}: "
                f"{exception}"
            )

    if failures > 0:
        raise SystemExit(
            f"{failures} experiment "
            "dataset(s) failed"
        )


if __name__ == "__main__":
    main()