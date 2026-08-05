from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Compare saved Random Forest and XGBoost metrics."
    )
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=Path("scripts/ml-training/output"),
    )
    args = parser.parse_args()

    output_directory = args.output_directory.resolve()
    metrics_path = output_directory / "model-metrics.json"

    if not metrics_path.is_file():
        raise FileNotFoundError(
            f"Missing metrics file: {metrics_path}. Run train_models.py first."
        )

    with metrics_path.open("r", encoding="utf-8") as file:
        metrics = json.load(file)

    rows: list[dict[str, object]] = []

    for model_name, model_metrics in metrics["models"].items():
        for split_name in ["validation", "test"]:
            rows.append(
                {
                    "model": model_name,
                    "split": split_name,
                    **model_metrics[split_name],
                }
            )

    comparison = pd.DataFrame(rows).sort_values(
        by=["split", "mae_ms"]
    )

    comparison_path = output_directory / "model-comparison.csv"
    comparison.to_csv(comparison_path, index=False)

    print(
        comparison.to_string(
            index=False,
            float_format=lambda value: f"{value:.4f}",
        )
    )

    validation_rows = comparison[
        comparison["split"] == "validation"
        ]

    best_model = validation_rows.loc[
        validation_rows["mae_ms"].idxmin(),
        "model",
    ]

    summary = (
        "Development model comparison\n"
        "============================\n"
        f"Best validation MAE: {best_model}\n"
        f"Split strategy: {metrics['split_strategy']}\n\n"
        "These results are provisional until training, validation, and "
        "testing are split by complete experiment traces/runs.\n"
    )

    summary_path = output_directory / "evaluation-summary.txt"
    summary_path.write_text(summary, encoding="utf-8")

    print(f"\nComparison written to: {comparison_path}")
    print(f"Summary written to: {summary_path}")


if __name__ == "__main__":
    main()
