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
        "mean_processing_time_ms": float(processing_time.mean()),
        "p95_processing_time_ms": percentile(processing_time, 0.95),
    }


def calculate_backlog_metrics(queue_metrics: pd.DataFrame) -> dict:
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

def calculate_queue_imbalance(queue_metrics: pd.DataFrame) -> dict:
    """
    Queue imbalance is the standard deviation of the three queue
    lengths at each sampling point.

    The run-level metrics summarise the resulting imbalance values.
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

    # Population standard deviation across the three queues.
    imbalance = valid.std(axis=1, ddof=0)

    return {
        "mean_queue_imbalance": float(imbalance.mean()),
        "median_queue_imbalance": float(imbalance.median()),
        "p95_queue_imbalance": percentile(imbalance, 0.95),
        "max_queue_imbalance": float(imbalance.max()),
    }

def calculate_recovery_metrics(
        queue_metrics: pd.DataFrame,
        metadata: dict,
) -> dict:
    """
    Recovery time is measured from the configured end of the burst
    until aggregate backlog returns to or below the median pre-burst
    backlog for three consecutive queue-sampling intervals.

    Recovery is reported only for burst-enabled runs.
    """
    burst_enabled = metadata.get("burst_enabled", False)

    if isinstance(burst_enabled, str):
        burst_enabled = burst_enabled.strip().lower() == "true"

    if not burst_enabled:
        return {
            "pre_burst_median_backlog": float("nan"),
            "recovery_time_seconds": float("nan"),
            "recovery_observed": False,
        }

    burst_start_seconds = metadata.get("burst_start_seconds")
    burst_duration_seconds = metadata.get("burst_duration_seconds")

    if burst_start_seconds is None or burst_duration_seconds is None:
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

    recovered = post_burst["at_or_below_baseline"].to_numpy()
    elapsed = post_burst["elapsed_ms"].to_numpy()

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
    Throughput is measured as completed events divided by the elapsed
    interval between the earliest consumer start and latest consumer
    completion.

    This measures observed consumer-side processing throughput rather
    than RabbitMQ's acknowledgement-rate feature.
    """
    starts = pd.to_numeric(
        events["consumer_started_at"],
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

    interval_seconds = (last_completion - first_start) / 1000.0

    if interval_seconds <= 0:
        return {
            "throughput_events_per_second": float("nan"),
            "throughput_interval_seconds": float("nan"),
        }

    completed_events = int(valid.sum())

    return {
        "throughput_events_per_second": completed_events / interval_seconds,
        "throughput_interval_seconds": interval_seconds,
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

    # Convert nanoseconds to microseconds for easier interpretation.
    overhead_us = overhead_ns / 1_000.0

    return {
        "routing_decision_count": int(len(overhead_us)),
        "mean_routing_overhead_us": float(overhead_us.mean()),
        "median_routing_overhead_us": float(overhead_us.median()),
        "p95_routing_overhead_us": percentile(overhead_us, 0.95),
        "p99_routing_overhead_us": percentile(overhead_us, 0.99),
    }


def calculate_queue_distribution(events: pd.DataFrame) -> dict:
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


def analyse_run(run_dir: Path) -> dict:
    for filename in REQUIRED_FILES:
        path = run_dir / filename

        if not path.exists():
            raise FileNotFoundError(
                f"Run directory {run_dir} is missing {filename}"
            )

    metadata = load_metadata(run_dir)

    events = load_csv(run_dir, "event_metrics.csv")
    queue_metrics = load_csv(run_dir, "queue_metrics.csv")
    routing_metrics = load_csv(run_dir, "routing_metrics.csv")

    summary = {
        "run_id": metadata.get("run_id"),
        "strategy": metadata.get("strategy"),
        "random_seed": metadata.get("random_seed"),
        "workload_duration_seconds": metadata.get(
            "workload_duration_seconds"
        ),
        "arrival_scale": metadata.get("arrival_scale"),
        "burst_enabled": metadata.get("burst_enabled"),
        "burst_start_seconds": metadata.get(
            "burst_start_seconds"
        ),
        "burst_duration_seconds": metadata.get(
            "burst_duration_seconds"
        ),
        "burst_multiplier": metadata.get(
            "burst_multiplier"
        ),
        "queue_sampling_interval_ms": metadata.get(
            "queue_sampling_interval_ms"
        ),
    }

    summary.update(calculate_latency_metrics(events))
    summary.update(calculate_processing_metrics(events))
    summary.update(calculate_backlog_metrics(queue_metrics))
    summary.update(calculate_queue_imbalance(queue_metrics))
    summary.update(calculate_throughput(events))
    summary.update(
        calculate_routing_overhead_metrics(routing_metrics)
    )
    summary.update(calculate_queue_distribution(events))
    summary.update(
        calculate_recovery_metrics(
            queue_metrics,
            metadata,
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
                    (run_directory / filename).is_file()
                    for filename in REQUIRED_FILES
            ):
                run_dirs.append(run_directory)

    return run_dirs


def analyse_all_runs(data_dir: Path) -> pd.DataFrame:
    run_dirs = find_run_directories(data_dir)

    if not run_dirs:
        raise RuntimeError(
            f"No valid experiment run directories found under {data_dir}"
        )

    summaries = []

    for run_dir in run_dirs:
        try:
            metadata = load_metadata(run_dir)

            strategy = str(
                metadata.get("strategy", "")
            ).strip().lower()

            if strategy == "training":
                print(f"Skipping training run: {run_dir.name}")
                continue

            print(f"Analysing {run_dir.name}...")
            summaries.append(analyse_run(run_dir))

        except Exception as exc:
            print(
                f"WARNING: Failed to analyse {run_dir.name}: {exc}"
            )

    if not summaries:
        raise RuntimeError(
            "No evaluation runs could be analysed successfully."
        )

    return pd.DataFrame(summaries)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Analyse routing experiment output and produce one "
            "summary row per experimental run."
        )
    )

    parser.add_argument(
        "--data-dir",
        type=Path,
        default=Path("data"),
        help=(
            "Directory containing experimental run directories "
            "(default: data)"
        ),
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=Path("data/evaluation_summary.csv"),
        help=(
            "Output CSV path "
            "(default: data/evaluation_summary.csv)"
        ),
    )

    args = parser.parse_args()

    if not args.data_dir.exists():
        raise FileNotFoundError(
            f"Data directory does not exist: {args.data_dir}"
        )

    results = analyse_all_runs(args.data_dir)

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
    print(f"Analysed {len(results)} run(s).")
    print(f"Summary written to: {args.output}")
    print()
    print(results.to_string(index=False))


if __name__ == "__main__":
    main()