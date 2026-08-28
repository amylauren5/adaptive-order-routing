from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd


REQUIRED_FILES = (
    "run_metadata.json",
    "event_metrics.csv",
    "queue_metrics.csv",
    "routing_metrics.csv",
)

EXPECTED_EVALUATION_RUNS = 45


def load_metadata(run_dir: Path) -> dict:
    metadata_path = run_dir / "run_metadata.json"

    with metadata_path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_csv(run_dir: Path, filename: str) -> pd.DataFrame:
    path = run_dir / filename

    if not path.exists():
        raise FileNotFoundError(f"Missing required file: {path}")

    return pd.read_csv(path)


def percentile(series: pd.Series, quantile: float) -> float:
    values = pd.to_numeric(series, errors="coerce").dropna()

    if values.empty:
        return float("nan")

    return float(values.quantile(quantile))


def as_bool(value) -> bool:
    if isinstance(value, str):
        return value.strip().lower() == "true"

    return bool(value)


def validate_run_integrity(
        run_dir: Path,
        metadata: dict,
        events: pd.DataFrame,
        queue_metrics: pd.DataFrame,
        routing_metrics: pd.DataFrame,
) -> None:
    run_id = metadata.get("run_id")

    if not run_id:
        raise ValueError(f"{run_dir}: run metadata has no run_id")

    if events.empty:
        raise ValueError(f"{run_id}: event_metrics.csv is empty")

    if queue_metrics.empty:
        raise ValueError(f"{run_id}: queue_metrics.csv is empty")

    if routing_metrics.empty:
        raise ValueError(f"{run_id}: routing_metrics.csv is empty")

    for dataframe_name, dataframe in (
            ("event_metrics.csv", events),
            ("queue_metrics.csv", queue_metrics),
            ("routing_metrics.csv", routing_metrics),
    ):
        if "run_id" not in dataframe.columns:
            raise ValueError(
                f"{run_id}: {dataframe_name} has no run_id column"
            )

        observed_run_ids = set(
            dataframe["run_id"].dropna().astype(str)
        )

        if observed_run_ids != {str(run_id)}:
            raise ValueError(
                f"{run_id}: unexpected run_id values in "
                f"{dataframe_name}: {sorted(observed_run_ids)}"
            )

    required_event_columns = {
        "routing_decision_id",
        "selected_queue",
        "published_at",
        "processing_started_at",
        "consumer_completed_at",
        "queueing_latency_ms",
        "processing_time_ms",
    }

    missing_event_columns = (
            required_event_columns - set(events.columns)
    )

    if missing_event_columns:
        raise ValueError(
            f"{run_id}: event_metrics.csv missing columns: "
            f"{sorted(missing_event_columns)}"
        )

    required_routing_columns = {
        "routing_decision_id",
        "selected_queue",
        "routing_overhead_ns",
    }

    missing_routing_columns = (
            required_routing_columns - set(routing_metrics.columns)
    )

    if missing_routing_columns:
        raise ValueError(
            f"{run_id}: routing_metrics.csv missing columns: "
            f"{sorted(missing_routing_columns)}"
        )

    required_queue_columns = {
        "elapsed_ms",
        "phase",
        "q1_length",
        "q2_length",
        "q3_length",
        "aggregate_backlog",
    }

    missing_queue_columns = (
            required_queue_columns - set(queue_metrics.columns)
    )

    if missing_queue_columns:
        raise ValueError(
            f"{run_id}: queue_metrics.csv missing columns: "
            f"{sorted(missing_queue_columns)}"
        )

    event_ids = events["routing_decision_id"].astype(str)
    routing_ids = routing_metrics["routing_decision_id"].astype(str)

    if event_ids.duplicated().any():
        duplicates = event_ids[event_ids.duplicated()].unique()
        raise ValueError(
            f"{run_id}: duplicate routing_decision_id values "
            f"in event_metrics.csv: {duplicates[:5].tolist()}"
        )

    if routing_ids.duplicated().any():
        duplicates = routing_ids[routing_ids.duplicated()].unique()
        raise ValueError(
            f"{run_id}: duplicate routing_decision_id values "
            f"in routing_metrics.csv: {duplicates[:5].tolist()}"
        )

    event_id_set = set(event_ids)
    routing_id_set = set(routing_ids)

    if event_id_set != routing_id_set:
        missing_from_events = routing_id_set - event_id_set
        missing_from_routing = event_id_set - routing_id_set

        raise ValueError(
            f"{run_id}: routing/event decision IDs do not match. "
            f"Missing from events={len(missing_from_events)}, "
            f"missing from routing={len(missing_from_routing)}"
        )

    joined = events[
        ["routing_decision_id", "selected_queue"]
    ].merge(
        routing_metrics[
            ["routing_decision_id", "selected_queue"]
        ],
        on="routing_decision_id",
        how="inner",
        suffixes=("_event", "_routing"),
        validate="one_to_one",
    )

    queue_mismatch = (
            joined["selected_queue_event"]
            != joined["selected_queue_routing"]
    )

    if queue_mismatch.any():
        raise ValueError(
            f"{run_id}: selected_queue differs between "
            f"routing and event metrics for "
            f"{int(queue_mismatch.sum())} event(s)"
        )

    workload_duration_seconds = metadata.get(
        "workload_duration_seconds"
    )

    if workload_duration_seconds is None:
        raise ValueError(
            f"{run_id}: workload_duration_seconds missing "
            "from metadata"
        )

    elapsed = pd.to_numeric(
        queue_metrics["elapsed_ms"],
        errors="coerce",
    )

    active_window = queue_metrics[
        elapsed <= float(workload_duration_seconds) * 1000.0
        ]

    if active_window.empty:
        raise ValueError(
            f"{run_id}: no queue samples found during "
            "the workload-generation window"
        )


def select_active_queue_window(
        queue_metrics: pd.DataFrame,
        metadata: dict,
) -> pd.DataFrame:
    """
    Select queue samples collected while new workload is being
    generated.

    General backlog and queue-imbalance summaries are restricted
    to this common observation window so that runs with different
    drain durations are compared over an equivalent period.

    Recovery is calculated separately and may use later samples.
    """
    workload_duration_seconds = metadata.get(
        "workload_duration_seconds"
    )

    if workload_duration_seconds is None:
        raise ValueError(
            "workload_duration_seconds is missing from metadata"
        )

    metrics = queue_metrics.copy()

    metrics["elapsed_ms"] = pd.to_numeric(
        metrics["elapsed_ms"],
        errors="coerce",
    )

    metrics = metrics.dropna(
        subset=["elapsed_ms"]
    ).sort_values("elapsed_ms")

    workload_end_ms = (
            float(workload_duration_seconds) * 1000.0
    )

    return metrics[
        (metrics["elapsed_ms"] >= 0)
        & (metrics["elapsed_ms"] <= workload_end_ms)
        ].copy()


def calculate_latency_metrics(events: pd.DataFrame) -> dict:
    latency = pd.to_numeric(
        events["queueing_latency_ms"],
        errors="coerce",
    ).dropna()

    if latency.empty:
        return {
            "event_count": 0,
            "mean_queueing_latency_ms": float("nan"),
            "median_queueing_latency_ms": float("nan"),
            "p95_queueing_latency_ms": float("nan"),
            "p99_queueing_latency_ms": float("nan"),
        }

    return {
        "event_count": int(len(latency)),
        "mean_queueing_latency_ms": float(latency.mean()),
        "median_queueing_latency_ms": float(latency.median()),
        "p95_queueing_latency_ms": percentile(latency, 0.95),
        "p99_queueing_latency_ms": percentile(latency, 0.99),
    }


def calculate_processing_metrics(events: pd.DataFrame) -> dict:
    processing_time = pd.to_numeric(
        events["processing_time_ms"],
        errors="coerce",
    ).dropna()

    if processing_time.empty:
        return {
            "mean_processing_time_ms": float("nan"),
            "p95_processing_time_ms": float("nan"),
        }

    return {
        "mean_processing_time_ms": float(
            processing_time.mean()
        ),
        "p95_processing_time_ms": percentile(
            processing_time,
            0.95,
        ),
    }


def calculate_backlog_metrics(
        queue_metrics: pd.DataFrame,
) -> dict:
    backlog = pd.to_numeric(
        queue_metrics["aggregate_backlog"],
        errors="coerce",
    ).dropna()

    if backlog.empty:
        return {
            "mean_backlog": float("nan"),
            "median_backlog": float("nan"),
            "p95_backlog": float("nan"),
            "max_backlog": float("nan"),
        }

    return {
        "mean_backlog": float(backlog.mean()),
        "median_backlog": float(backlog.median()),
        "p95_backlog": percentile(backlog, 0.95),
        "max_backlog": float(backlog.max()),
    }


def calculate_queue_imbalance(
        queue_metrics: pd.DataFrame,
) -> dict:
    """
    Queue imbalance is the population standard deviation of the
    three queue lengths at each queue-sampling point.
    """
    queue_lengths = queue_metrics[
        ["q1_length", "q2_length", "q3_length"]
    ].apply(
        pd.to_numeric,
        errors="coerce",
    )

    valid = queue_lengths.dropna()

    if valid.empty:
        return {
            "mean_queue_imbalance": float("nan"),
            "median_queue_imbalance": float("nan"),
            "p95_queue_imbalance": float("nan"),
            "max_queue_imbalance": float("nan"),
        }

    imbalance = valid.std(axis=1, ddof=0)

    return {
        "mean_queue_imbalance": float(
            imbalance.mean()
        ),
        "median_queue_imbalance": float(
            imbalance.median()
        ),
        "p95_queue_imbalance": percentile(
            imbalance,
            0.95,
        ),
        "max_queue_imbalance": float(
            imbalance.max()
        ),
    }


def calculate_recovery_metrics(
        queue_metrics: pd.DataFrame,
        metadata: dict,
) -> dict:
    """
    Recovery time is measured from the configured burst end until
    aggregate backlog returns to or below the median pre-burst
    backlog for three consecutive sampling intervals.

    Post-generation samples remain eligible because recovery may
    occur during the drain phase.
    """
    burst_enabled = as_bool(
        metadata.get("burst_enabled", False)
    )

    if not burst_enabled:
        return {
            "pre_burst_median_backlog": float("nan"),
            "recovery_time_seconds": float("nan"),
            "recovery_observed": False,
        }

    burst_start_seconds = metadata.get(
        "burst_start_seconds"
    )
    burst_duration_seconds = metadata.get(
        "burst_duration_seconds"
    )

    if (
            burst_start_seconds is None
            or burst_duration_seconds is None
    ):
        return {
            "pre_burst_median_backlog": float("nan"),
            "recovery_time_seconds": float("nan"),
            "recovery_observed": False,
        }

    metrics = queue_metrics.copy()

    metrics["elapsed_ms"] = pd.to_numeric(
        metrics["elapsed_ms"],
        errors="coerce",
    )

    metrics["aggregate_backlog"] = pd.to_numeric(
        metrics["aggregate_backlog"],
        errors="coerce",
    )

    metrics = metrics.dropna(
        subset=["elapsed_ms", "aggregate_backlog"]
    ).sort_values("elapsed_ms")

    pre_burst = metrics[
        metrics["phase"] == "PRE_BURST"
        ]

    if pre_burst.empty:
        return {
            "pre_burst_median_backlog": float("nan"),
            "recovery_time_seconds": float("nan"),
            "recovery_observed": False,
        }

    baseline = float(
        pre_burst["aggregate_backlog"].median()
    )

    burst_end_ms = (
                           float(burst_start_seconds)
                           + float(burst_duration_seconds)
                   ) * 1000.0

    post_burst = metrics[
        (metrics["phase"] == "POST_BURST")
        & (metrics["elapsed_ms"] >= burst_end_ms)
        ].copy()

    if len(post_burst) < 3:
        return {
            "pre_burst_median_backlog": baseline,
            "recovery_time_seconds": float("nan"),
            "recovery_observed": False,
        }

    post_burst["at_or_below_baseline"] = (
            post_burst["aggregate_backlog"] <= baseline
    )

    recovered = (
        post_burst["at_or_below_baseline"]
        .to_numpy()
    )

    elapsed = (
        post_burst["elapsed_ms"]
        .to_numpy()
    )

    for index in range(len(recovered) - 2):
        if (
                recovered[index]
                and recovered[index + 1]
                and recovered[index + 2]
        ):
            recovery_time_seconds = (
                                            elapsed[index] - burst_end_ms
                                    ) / 1000.0

            return {
                "pre_burst_median_backlog": baseline,
                "recovery_time_seconds": float(
                    max(0.0, recovery_time_seconds)
                ),
                "recovery_observed": True,
            }

    return {
        "pre_burst_median_backlog": baseline,
        "recovery_time_seconds": float("nan"),
        "recovery_observed": False,
    }


def calculate_throughput(events: pd.DataFrame) -> dict:
    """
    Observed consumer-side throughput is completed events divided
    by the elapsed interval between the first processing start and
    the final processing completion.

    The interval includes drain time where required so that all
    completed events in the run are represented.
    """
    starts = pd.to_numeric(
        events["processing_started_at"],
        errors="coerce",
    )

    completions = pd.to_numeric(
        events["consumer_completed_at"],
        errors="coerce",
    )

    valid = starts.notna() & completions.notna()

    if not valid.any():
        return {
            "throughput_events_per_second": float("nan"),
            "throughput_interval_seconds": float("nan"),
        }

    first_start = starts[valid].min()
    last_completion = completions[valid].max()

    interval_seconds = (
                               last_completion - first_start
                       ) / 1000.0

    if interval_seconds <= 0:
        return {
            "throughput_events_per_second": float("nan"),
            "throughput_interval_seconds": float("nan"),
        }

    completed_events = int(valid.sum())

    return {
        "throughput_events_per_second":
            completed_events / interval_seconds,
        "throughput_interval_seconds":
            float(interval_seconds),
    }


def calculate_routing_overhead_metrics(
        routing_metrics: pd.DataFrame,
) -> dict:
    overhead_ns = pd.to_numeric(
        routing_metrics["routing_overhead_ns"],
        errors="coerce",
    ).dropna()

    if overhead_ns.empty:
        return {
            "routing_decision_count": 0,
            "mean_routing_overhead_us": float("nan"),
            "median_routing_overhead_us": float("nan"),
            "p95_routing_overhead_us": float("nan"),
            "p99_routing_overhead_us": float("nan"),
        }

    overhead_us = overhead_ns / 1_000.0

    return {
        "routing_decision_count": int(
            len(overhead_us)
        ),
        "mean_routing_overhead_us": float(
            overhead_us.mean()
        ),
        "median_routing_overhead_us": float(
            overhead_us.median()
        ),
        "p95_routing_overhead_us": percentile(
            overhead_us,
            0.95,
        ),
        "p99_routing_overhead_us": percentile(
            overhead_us,
            0.99,
        ),
    }


def calculate_model_inference_metrics(
        routing_metrics: pd.DataFrame,
) -> dict:
    if "model_inference_ns" not in routing_metrics.columns:
        return {
            "mean_model_inference_us": float("nan"),
            "median_model_inference_us": float("nan"),
            "p95_model_inference_us": float("nan"),
            "p99_model_inference_us": float("nan"),
        }

    inference_ns = pd.to_numeric(
        routing_metrics["model_inference_ns"],
        errors="coerce",
    )

    inference_ns = inference_ns[
        inference_ns > 0
        ].dropna()

    if inference_ns.empty:
        return {
            "mean_model_inference_us": float("nan"),
            "median_model_inference_us": float("nan"),
            "p95_model_inference_us": float("nan"),
            "p99_model_inference_us": float("nan"),
        }

    inference_us = inference_ns / 1_000.0

    return {
        "mean_model_inference_us": float(
            inference_us.mean()
        ),
        "median_model_inference_us": float(
            inference_us.median()
        ),
        "p95_model_inference_us": percentile(
            inference_us,
            0.95,
        ),
        "p99_model_inference_us": percentile(
            inference_us,
            0.99,
        ),
    }


def calculate_queue_distribution(
        events: pd.DataFrame,
) -> dict:
    counts = events["selected_queue"].value_counts()

    return {
        "q1_event_count": int(
            counts.get("processing.queue-1", 0)
        ),
        "q2_event_count": int(
            counts.get("processing.queue-2", 0)
        ),
        "q3_event_count": int(
            counts.get("processing.queue-3", 0)
        ),
    }


def calculate_sampling_quality(
        queue_metrics: pd.DataFrame,
        active_queue_metrics: pd.DataFrame,
) -> dict:
    """
    Report sampling quality separately for the active workload
    window and for the complete recorded queue-metrics file.

    The complete-file gap is diagnostic only. Post-workload
    orchestration or idle time must not be interpreted as an
    active-workload sampling failure.
    """

    def maximum_gap_seconds(
            dataframe: pd.DataFrame,
    ) -> float:
        elapsed = pd.to_numeric(
            dataframe["elapsed_ms"],
            errors="coerce",
        ).dropna().sort_values()

        if len(elapsed) < 2:
            return float("nan")

        gaps_ms = elapsed.diff().dropna()

        return float(
            gaps_ms.max() / 1000.0
        )

    return {
        "max_active_queue_sampling_gap_seconds":
            maximum_gap_seconds(
                active_queue_metrics
            ),
        "max_recorded_queue_sampling_gap_seconds":
            maximum_gap_seconds(
                queue_metrics
            ),
        "active_queue_sample_count":
            int(len(active_queue_metrics)),
        "recorded_queue_sample_count":
            int(len(queue_metrics)),
    }


def analyse_run(run_dir: Path) -> dict:
    for filename in REQUIRED_FILES:
        path = run_dir / filename

        if not path.exists():
            raise FileNotFoundError(
                f"Run directory {run_dir} "
                f"is missing {filename}"
            )

    metadata = load_metadata(run_dir)

    events = load_csv(
        run_dir,
        "event_metrics.csv",
    )

    queue_metrics = load_csv(
        run_dir,
        "queue_metrics.csv",
    )

    routing_metrics = load_csv(
        run_dir,
        "routing_metrics.csv",
    )

    validate_run_integrity(
        run_dir,
        metadata,
        events,
        queue_metrics,
        routing_metrics,
    )

    active_queue_metrics = (
        select_active_queue_window(
            queue_metrics,
            metadata,
        )
    )

    summary = {
        "run_id": metadata.get("run_id"),
        "strategy": metadata.get("strategy"),
        "random_seed": metadata.get("random_seed"),
        "workload_duration_seconds":
            metadata.get(
                "workload_duration_seconds"
            ),
        "arrival_scale":
            metadata.get("arrival_scale"),
        "burst_enabled":
            metadata.get("burst_enabled"),
        "burst_start_seconds":
            metadata.get(
                "burst_start_seconds"
            ),
        "burst_duration_seconds":
            metadata.get(
                "burst_duration_seconds"
            ),
        "burst_multiplier":
            metadata.get(
                "burst_multiplier"
            ),
        "queue_sampling_interval_ms":
            metadata.get(
                "queue_sampling_interval_ms"
            ),
    }

    summary.update(
        calculate_latency_metrics(events)
    )

    summary.update(
        calculate_processing_metrics(events)
    )

    summary.update(
        calculate_backlog_metrics(
            active_queue_metrics
        )
    )

    summary.update(
        calculate_queue_imbalance(
            active_queue_metrics
        )
    )

    summary.update(
        calculate_throughput(events)
    )

    summary.update(
        calculate_routing_overhead_metrics(
            routing_metrics
        )
    )

    summary.update(
        calculate_queue_distribution(events)
    )

    summary.update(
        calculate_recovery_metrics(
            queue_metrics,
            metadata,
        )
    )

    summary.update(
        calculate_model_inference_metrics(
            routing_metrics
        )
    )

    summary.update(
        calculate_sampling_quality(
            queue_metrics,
            active_queue_metrics,
        )
    )

    return summary


def find_run_directories(
        data_dir: Path,
) -> list[Path]:
    run_dirs: list[Path] = []

    evaluation_directories = [
        data_dir / "shortest-queue",
        data_dir / "little-law",
        data_dir / "ml",
        ]

    for strategy_directory in evaluation_directories:
        if not strategy_directory.is_dir():
            continue

        for run_directory in sorted(
                strategy_directory.iterdir()
        ):
            if not run_directory.is_dir():
                continue

            if all(
                    (
                            run_directory / filename
                    ).is_file()
                    for filename in REQUIRED_FILES
            ):
                run_dirs.append(
                    run_directory
                )

    return run_dirs


def analyse_all_runs(
        data_dir: Path,
) -> pd.DataFrame:
    run_dirs = find_run_directories(
        data_dir
    )

    if not run_dirs:
        raise RuntimeError(
            "No valid experiment run directories "
            f"found under {data_dir}"
        )

    summaries = []

    for run_dir in run_dirs:
        metadata = load_metadata(
            run_dir
        )

        strategy = str(
            metadata.get(
                "strategy",
                "",
            )
        ).strip().lower()

        if strategy == "training":
            print(
                f"Skipping training run: "
                f"{run_dir.name}"
            )
            continue

        print(
            f"Analysing {run_dir.name}..."
        )

        summaries.append(
            analyse_run(run_dir)
        )

    if not summaries:
        raise RuntimeError(
            "No evaluation runs could "
            "be analysed successfully."
        )

    results = pd.DataFrame(
        summaries
    )

    if len(results) != EXPECTED_EVALUATION_RUNS:
        raise RuntimeError(
            "Expected "
            f"{EXPECTED_EVALUATION_RUNS} "
            "final evaluation runs, but "
            f"analysed {len(results)}."
        )

    duplicate_run_ids = (
        results["run_id"]
        .duplicated()
    )

    if duplicate_run_ids.any():
        duplicates = results.loc[
            duplicate_run_ids,
            "run_id",
        ].tolist()

        raise RuntimeError(
            "Duplicate run_id values "
            f"found: {duplicates}"
        )

    strategy_counts = (
        results["strategy"]
        .value_counts()
        .to_dict()
    )

    expected_strategy_counts = {
        "shortest-queue": 15,
        "little-law": 15,
        "ml": 15,
    }

    if strategy_counts != expected_strategy_counts:
        raise RuntimeError(
            "Unexpected strategy run counts. "
            f"Expected "
            f"{expected_strategy_counts}, "
            f"observed {strategy_counts}"
        )

    return results


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Analyse routing experiment output "
            "and produce one summary row per "
            "experimental run."
        )
    )

    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("data"),
        help=(
            "Directory containing experimental "
            "run directories (default: data)"
        ),
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=Path(
            "data/evaluation_summary.csv"
        ),
        help=(
            "Output CSV path "
            "(default: "
            "data/evaluation_summary.csv)"
        ),
    )

    args = parser.parse_args()

    if not args.data_dir.exists():
        raise FileNotFoundError(
            "Data directory does not exist: "
            f"{args.data_dir}"
        )

    results = analyse_all_runs(
        args.data_dir
    )

    sort_columns = [
        column
        for column in (
            "strategy",
            "arrival_scale",
            "burst_enabled",
            "burst_multiplier",
            "burst_duration_seconds",
            "random_seed",
        )
        if column in results.columns
    ]

    if sort_columns:
        results = results.sort_values(
            sort_columns,
            na_position="last",
        )

    args.output.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    results.to_csv(
        args.output,
        index=False,
        float_format="%.6f",
    )

    print()
    print(
        f"Analysed {len(results)} run(s)."
    )
    print(
        f"Summary written to: "
        f"{args.output}"
    )

    print()
    print(
        "Strategy counts:"
    )
    print(
        results["strategy"]
        .value_counts()
        .sort_index()
        .to_string()
    )

    print()
    print(
        "Maximum active-workload "
        "queue-sampling gap:"
    )
    print(
        f"{results[
            'max_active_queue_sampling_gap_seconds'
        ].max():.3f} s"
    )


if __name__ == "__main__":
    main()