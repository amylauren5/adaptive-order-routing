from __future__ import annotations

import argparse
import math
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from scipy.stats import rankdata, t, wilcoxon


EXPECTED_STRATEGIES = ("shortest-queue", "little-law", "ml")
EXPECTED_SEEDS = (1001, 1002, 1003, 1004, 1005)

# Thesis-friendly Seaborn styling applied to all Matplotlib figures.
sns.set_theme(
    context="paper",
    style="whitegrid",
    font_scale=1.15,
    rc={
        "axes.spines.top": False,
        "axes.spines.right": False,
        "axes.titleweight": "normal",
        "axes.labelsize": 11,
        "xtick.labelsize": 10,
        "ytick.labelsize": 10,
        "legend.fontsize": 10,
        "figure.dpi": 100,
        "savefig.dpi": 300,
    },
)

# Colourblind-safe palette for consistent strategy colours.
SEABORN_PALETTE = sns.color_palette("colorblind", n_colors=3)

# Metrics used in the final system-level evaluation.
METRICS = {
    "median_queueing_latency_ms": {
        "label": "Median realised waiting time (ms)",
        "lower_is_better": True,
        "figure": "median_realised_waiting_time",
    },
    "p95_queueing_latency_ms": {
        "label": "P95 realised waiting time (ms)",
        "lower_is_better": True,
        "figure": "p95_realised_waiting_time",
    },
    "p99_queueing_latency_ms": {
        "label": "P99 realised waiting time (ms)",
        "lower_is_better": True,
        "figure": "p99_realised_waiting_time",
    },
    "mean_backlog": {
        "label": "Mean aggregate backlog",
        "lower_is_better": True,
        "figure": "mean_backlog",
    },
    "p95_backlog": {
        "label": "P95 aggregate backlog",
        "lower_is_better": True,
        "figure": "p95_backlog",
    },
    "mean_queue_imbalance": {
        "label": "Mean queue imbalance",
        "lower_is_better": True,
        "figure": "mean_queue_imbalance",
    },
    "p95_queue_imbalance": {
        "label": "P95 queue imbalance",
        "lower_is_better": True,
        "figure": "p95_queue_imbalance",
    },
    "throughput_events_per_second": {
        "label": "Throughput (events/s)",
        "lower_is_better": False,
        "figure": "throughput",
    },
    "recovery_time_seconds": {
        "label": "Recovery time (s)",
        "lower_is_better": True,
        "figure": "recovery_time",
    },
    "median_routing_overhead_ms": {
        "label": "Median routing overhead (ms)",
        "lower_is_better": True,
        "figure": "routing_overhead",
    },
}

# ML is compared separately against both deterministic baselines.
PRIMARY_COMPARISONS = (
    ("ml", "shortest-queue"),
    ("ml", "little-law"),
)

CONDITION_ORDER = (
    "steady-medium",
    "strong-short-burst",
    "moderate-long-burst",
)

CONDITION_LABELS = {
    "steady-medium": "Steady workload",
    "strong-short-burst": "Strong short burst",
    "moderate-long-burst": "Moderate long burst",
}

STRATEGY_LABELS = {
    "shortest-queue": "Shortest queue",
    "little-law": "Little's Law",
    "ml": "ML",
}

STRATEGY_COLOURS = {
    "shortest-queue": SEABORN_PALETTE[0],
    "little-law": SEABORN_PALETTE[1],
    "ml": SEABORN_PALETTE[2],
}


def classify_condition(row: pd.Series) -> str:
    """Map run configuration fields to the three final workload conditions."""
    burst_enabled = bool(row["burst_enabled"])

    if not burst_enabled:
        return "steady-medium"

    multiplier = float(row["burst_multiplier"])
    duration = float(row["burst_duration_seconds"])

    if math.isclose(multiplier, 0.25) and math.isclose(duration, 5.0):
        return "strong-short-burst"

    if math.isclose(multiplier, 0.50) and math.isclose(duration, 10.0):
        return "moderate-long-burst"

    raise ValueError(
        "Unexpected evaluation condition: "
        f"burst_enabled={burst_enabled}, "
        f"burst_multiplier={multiplier}, "
        f"burst_duration_seconds={duration}"
    )


def validate_summary(df: pd.DataFrame) -> None:
    """Validate the expected 3×3×5 final evaluation design."""
    required_columns = {
        "run_id",
        "strategy",
        "random_seed",
        "burst_enabled",
        "burst_multiplier",
        "burst_duration_seconds",
        *METRICS.keys(),
    }

    missing = required_columns - set(df.columns)
    if missing:
        raise ValueError(f"Summary CSV is missing columns: {sorted(missing)}")

    if len(df) != 45:
        raise ValueError(f"Expected 45 evaluation rows, found {len(df)}")

    if df["run_id"].duplicated().any():
        duplicates = df.loc[df["run_id"].duplicated(), "run_id"].tolist()
        raise ValueError(f"Duplicate run IDs found: {duplicates}")

    observed_strategies = set(df["strategy"].astype(str))
    if observed_strategies != set(EXPECTED_STRATEGIES):
        raise ValueError(
            f"Unexpected strategies. Expected {EXPECTED_STRATEGIES}, "
            f"observed {sorted(observed_strategies)}"
        )

    expected_cells = {
        (condition, strategy)
        for condition in CONDITION_ORDER
        for strategy in EXPECTED_STRATEGIES
    }

    observed_cells = set(zip(df["condition"], df["strategy"]))

    if observed_cells != expected_cells:
        raise ValueError(
            "Condition/strategy cells do not match the 3×3 design. "
            f"Missing={sorted(expected_cells - observed_cells)}, "
            f"unexpected={sorted(observed_cells - expected_cells)}"
        )

    # Each condition/strategy cell must contain the same five paired seeds.
    for condition, strategy in sorted(expected_cells):
        cell = df[
            (df["condition"] == condition)
            & (df["strategy"] == strategy)
            ]

        seeds = tuple(sorted(cell["random_seed"].astype(int)))

        if seeds != EXPECTED_SEEDS:
            raise ValueError(
                f"{condition}/{strategy}: expected seeds {EXPECTED_SEEDS}, "
                f"found {seeds}"
            )


def descriptive_summary(df: pd.DataFrame) -> pd.DataFrame:
    """
    Summarise run-level metrics across the five seeded runs.

    The experimental run remains the unit of analysis.
    """
    rows = []

    for condition in CONDITION_ORDER:
        for strategy in EXPECTED_STRATEGIES:
            cell = df[
                (df["condition"] == condition)
                & (df["strategy"] == strategy)
                ]

            for metric, meta in METRICS.items():
                values = pd.to_numeric(
                    cell[metric],
                    errors="coerce",
                ).dropna()

                # Recovery is intentionally undefined for steady workload runs.
                if values.empty:
                    continue

                rows.append(
                    {
                        "condition": condition,
                        "condition_label": CONDITION_LABELS[condition],
                        "strategy": strategy,
                        "strategy_label": STRATEGY_LABELS[strategy],
                        "metric": metric,
                        "metric_label": meta["label"],
                        "n_runs": int(len(values)),
                        "mean": float(values.mean()),
                        "std": (
                            float(values.std(ddof=1))
                            if len(values) > 1
                            else np.nan
                        ),
                        "median": float(values.median()),
                        "q1": float(values.quantile(0.25)),
                        "q3": float(values.quantile(0.75)),
                        "min": float(values.min()),
                        "max": float(values.max()),
                    }
                )

    return pd.DataFrame(rows)


def paired_t_confidence_interval(
        differences: np.ndarray,
) -> tuple[float, float]:
    """Return the 95% t-based CI for the mean paired difference."""
    differences = np.asarray(differences, dtype=float)
    differences = differences[np.isfinite(differences)]

    n = len(differences)

    if n < 2:
        return np.nan, np.nan

    mean_diff = float(np.mean(differences))
    sd_diff = float(np.std(differences, ddof=1))

    if sd_diff == 0:
        return mean_diff, mean_diff

    standard_error = sd_diff / math.sqrt(n)
    critical = float(t.ppf(0.975, df=n - 1))

    return (
        mean_diff - critical * standard_error,
        mean_diff + critical * standard_error,
    )


def rank_biserial_effect(differences: np.ndarray) -> float:
    """
    Matched-pairs rank-biserial correlation.

    Positive values mean the first strategy tends to have larger
    metric values than the second strategy; negative values mean
    it tends to have smaller values.
    """
    differences = np.asarray(differences, dtype=float)
    differences = differences[np.isfinite(differences)]
    differences = differences[differences != 0]

    if len(differences) == 0:
        return 0.0

    ranks = rankdata(np.abs(differences), method="average")

    positive = float(ranks[differences > 0].sum())
    negative = float(ranks[differences < 0].sum())

    denominator = positive + negative

    return (
        (positive - negative) / denominator
        if denominator
        else 0.0
    )


def wilcoxon_p_value(differences: np.ndarray) -> float:
    """Return the two-sided Wilcoxon signed-rank p-value."""
    differences = np.asarray(differences, dtype=float)
    differences = differences[np.isfinite(differences)]

    if len(differences) == 0 or np.all(differences == 0):
        return 1.0

    result = wilcoxon(
        differences,
        alternative="two-sided",
        zero_method="wilcox",
        method="auto",
    )

    return float(result.pvalue)


def paired_comparisons(
        df: pd.DataFrame,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Compare ML with each deterministic baseline using matched seeds."""
    comparison_rows = []
    seed_rows = []

    for condition in CONDITION_ORDER:
        condition_df = df[df["condition"] == condition]

        for metric, meta in METRICS.items():
            if (
                    metric == "recovery_time_seconds"
                    and condition == "steady-medium"
            ):
                continue

            for first, second in PRIMARY_COMPARISONS:
                first_values = condition_df[
                    condition_df["strategy"] == first
                    ][["random_seed", metric]].rename(
                    columns={metric: "first_value"}
                )

                second_values = condition_df[
                    condition_df["strategy"] == second
                    ][["random_seed", metric]].rename(
                    columns={metric: "second_value"}
                )

                # Merge by seed so each comparison uses the same workload draw.
                paired = first_values.merge(
                    second_values,
                    on="random_seed",
                    how="inner",
                    validate="one_to_one",
                ).dropna(
                    subset=[
                        "first_value",
                        "second_value",
                    ]
                )

                if paired.empty:
                    continue

                paired["difference_first_minus_second"] = (
                        paired["first_value"]
                        - paired["second_value"]
                )

                differences = paired[
                    "difference_first_minus_second"
                ].to_numpy(dtype=float)

                ci_low, ci_high = paired_t_confidence_interval(
                    differences
                )

                # Wins depend on whether smaller or larger values are preferable.
                if meta["lower_is_better"]:
                    first_wins = int((differences < 0).sum())
                    ties = int((differences == 0).sum())
                    second_wins = int((differences > 0).sum())
                else:
                    first_wins = int((differences > 0).sum())
                    ties = int((differences == 0).sum())
                    second_wins = int((differences < 0).sum())

                denominator = np.abs(
                    paired["second_value"].to_numpy(dtype=float)
                )

                percent_change = np.full(
                    differences.shape,
                    np.nan,
                    dtype=float,
                )

                np.divide(
                    differences * 100.0,
                    denominator,
                    out=percent_change,
                    where=denominator > 0,
                    )

                comparison_rows.append(
                    {
                        "condition": condition,
                        "condition_label": CONDITION_LABELS[condition],
                        "metric": metric,
                        "metric_label": meta["label"],
                        "first_strategy": first,
                        "first_strategy_label": STRATEGY_LABELS[first],
                        "second_strategy": second,
                        "second_strategy_label": STRATEGY_LABELS[second],
                        "n_pairs": int(len(paired)),
                        "mean_difference_first_minus_second": float(
                            np.mean(differences)
                        ),
                        "mean_difference_ci95_low": float(ci_low),
                        "mean_difference_ci95_high": float(ci_high),
                        "median_difference_first_minus_second": float(
                            np.median(differences)
                        ),
                        "median_percent_change_vs_second": float(
                            np.nanmedian(percent_change)
                        ),
                        "first_strategy_wins": first_wins,
                        "ties": ties,
                        "second_strategy_wins": second_wins,
                        "wilcoxon_two_sided_p": wilcoxon_p_value(
                            differences
                        ),
                        "matched_rank_biserial": rank_biserial_effect(
                            differences
                        ),
                        "lower_is_better": bool(meta["lower_is_better"]),
                    }
                )

                for row in paired.itertuples(index=False):
                    seed_rows.append(
                        {
                            "condition": condition,
                            "condition_label": CONDITION_LABELS[condition],
                            "metric": metric,
                            "metric_label": meta["label"],
                            "first_strategy": first,
                            "second_strategy": second,
                            "random_seed": int(row.random_seed),
                            "first_value": float(row.first_value),
                            "second_value": float(row.second_value),
                            "difference_first_minus_second": float(
                                row.difference_first_minus_second
                            ),
                        }
                    )

    return (
        pd.DataFrame(comparison_rows),
        pd.DataFrame(seed_rows),
    )


def create_metric_figure(
        df: pd.DataFrame,
        metric: str,
        output_dir: Path,
) -> None:
    """
    Plot median run-level values with Q1-Q3 error bars.

    Seaborn provides the styling; aggregation remains explicit.
    """
    meta = METRICS[metric]

    conditions = [
        condition
        for condition in CONDITION_ORDER
        if not (
                metric == "recovery_time_seconds"
                and condition == "steady-medium"
        )
    ]

    fig, ax = plt.subplots(figsize=(9.2, 5.2))

    x = np.arange(len(conditions))
    width = 0.24

    offsets = {
        "shortest-queue": -width,
        "little-law": 0.0,
        "ml": width,
    }

    for strategy in EXPECTED_STRATEGIES:
        medians = []
        lower_errors = []
        upper_errors = []

        for condition in conditions:
            cell = df[
                (df["condition"] == condition)
                & (df["strategy"] == strategy)
                ]

            values = pd.to_numeric(
                cell[metric],
                errors="coerce",
            ).dropna()

            if values.empty:
                medians.append(np.nan)
                lower_errors.append(0.0)
                upper_errors.append(0.0)
                continue

            # Figures show the median and IQR across the five seeded runs.
            median = float(values.median())
            q1 = float(values.quantile(0.25))
            q3 = float(values.quantile(0.75))

            medians.append(median)
            lower_errors.append(median - q1)
            upper_errors.append(q3 - median)

        positions = x + offsets[strategy]

        ax.bar(
            positions,
            medians,
            width=width,
            color=STRATEGY_COLOURS[strategy],
            alpha=0.9,
            label=STRATEGY_LABELS[strategy],
            yerr=np.array(
                [
                    lower_errors,
                    upper_errors,
                ]
            ),
            capsize=4,
            edgecolor="none",
            error_kw={
                "elinewidth": 1.1,
                "capthick": 1.1,
            },
        )

    ax.set_xticks(x)
    ax.set_xticklabels(
        [
            CONDITION_LABELS[condition]
            for condition in conditions
        ]
    )

    ax.set_ylabel(meta["label"])
    ax.set_xlabel("Workload condition")

    # Keep only subtle horizontal grid lines.
    ax.grid(
        axis="y",
        linewidth=0.8,
        alpha=0.35,
    )
    ax.grid(
        axis="x",
        visible=False,
    )

    ax.set_axisbelow(True)

    # Non-log metrics use a natural zero baseline.
    if metric != "median_routing_overhead_ms":
        ax.set_ylim(bottom=0)

    # Routing overhead spans roughly two orders of magnitude.
    if metric == "median_routing_overhead_ms":
        ax.set_yscale("log")

    ax.legend(
        frameon=False,
        ncol=3,
        loc="upper center",
        bbox_to_anchor=(0.5, 1.02),
    )

    sns.despine(
        ax=ax,
        top=True,
        right=True,
    )

    fig.tight_layout()

    stem = meta["figure"]

    # PNG is useful for inspection; PDF is used in the thesis.
    fig.savefig(
        output_dir / f"{stem}.png",
        dpi=300,
        bbox_inches="tight",
        )

    fig.savefig(
        output_dir / f"{stem}.pdf",
        bbox_inches="tight",
        )

    plt.close(fig)


def create_figures(
        df: pd.DataFrame,
        figures_dir: Path,
) -> None:
    """Generate the six figures used in Chapter 5."""
    figures_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    for metric in (
            "median_queueing_latency_ms",
            "p95_queueing_latency_ms",
            "mean_backlog",
            "mean_queue_imbalance",
            "recovery_time_seconds",
            "median_routing_overhead_ms",
    ):
        create_metric_figure(
            df,
            metric,
            figures_dir,
        )


def create_key_results_table(
        descriptive: pd.DataFrame,
        output_path: Path,
) -> None:
    """Create the compact median [Q1, Q3] table used for Chapter 5."""
    key_metrics = [
        "median_queueing_latency_ms",
        "p95_queueing_latency_ms",
        "mean_backlog",
        "mean_queue_imbalance",
        "throughput_events_per_second",
        "recovery_time_seconds",
        "median_routing_overhead_ms",
    ]

    selected = descriptive[
        descriptive["metric"].isin(key_metrics)
    ].copy()

    selected["median_iqr"] = selected.apply(
        lambda row: (
            f"{row['median']:.3f} "
            f"[{row['q1']:.3f}, {row['q3']:.3f}]"
        ),
        axis=1,
    )

    table = selected.pivot_table(
        index=[
            "condition_label",
            "metric_label",
        ],
        columns="strategy_label",
        values="median_iqr",
        aggfunc="first",
    ).reset_index()

    table.to_csv(
        output_path,
        index=False,
    )


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "Generate final Chapter 5 run-level summaries, paired "
            "comparisons, statistical results and figures."
        )
    )

    parser.add_argument(
        "--input",
        type=Path,
        default=Path("data/evaluation_summary.csv"),
        help="Run-level evaluation summary CSV.",
    )

    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("results/final-evaluation"),
        help="Directory for derived Chapter 5 outputs.",
    )

    args = parser.parse_args()

    if not args.input.is_file():
        raise FileNotFoundError(
            f"Input file not found: {args.input}"
        )

    df = pd.read_csv(args.input)

    # Raw measurements are stored in microseconds; Chapter 5 reports ms.
    if "median_routing_overhead_us" not in df.columns:
        raise ValueError(
            "Input CSV is missing column: "
            "median_routing_overhead_us"
        )

    df["median_routing_overhead_ms"] = (
            pd.to_numeric(
                df["median_routing_overhead_us"],
                errors="raise",
            )
            / 1000.0
    )

    # Remove the raw column to prevent accidental unit mixing downstream.
    df = df.drop(
        columns=["median_routing_overhead_us"]
    )

    df["condition"] = df.apply(
        classify_condition,
        axis=1,
    )

    validate_summary(df)

    args.output_dir.mkdir(
        parents=True,
        exist_ok=True,
    )

    figures_dir = args.output_dir / "figures"

    descriptive = descriptive_summary(df)

    comparisons, seed_differences = paired_comparisons(df)

    descriptive.to_csv(
        args.output_dir / "condition-summary.csv",
        index=False,
        float_format="%.6f",
        )

    comparisons.to_csv(
        args.output_dir / "paired-comparisons.csv",
        index=False,
        float_format="%.6f",
        )

    seed_differences.to_csv(
        args.output_dir / "paired-seed-differences.csv",
        index=False,
        float_format="%.6f",
        )

    create_key_results_table(
        descriptive,
        args.output_dir / "chapter5-key-results.csv",
        )

    create_figures(
        df,
        figures_dir,
    )

    print("Final evaluation analysis completed.")
    print(f"Input runs: {len(df)}")

    print(
        "Condition summary: "
        f"{args.output_dir / 'condition-summary.csv'}"
    )

    print(
        "Paired comparisons: "
        f"{args.output_dir / 'paired-comparisons.csv'}"
    )

    print(
        "Seed differences: "
        f"{args.output_dir / 'paired-seed-differences.csv'}"
    )

    print(
        "Key results table: "
        f"{args.output_dir / 'chapter5-key-results.csv'}"
    )

    print(f"Figures: {figures_dir}")

    print()

    print(
        "Important: inferential tests use only the five paired seeds per "
        "condition. With n=5, two-sided Wilcoxon tests have very low power; "
        "interpret paired differences, consistency, effect sizes and confidence "
        "intervals together rather than using p<0.05 as the sole criterion."
    )


if __name__ == "__main__":
    main()