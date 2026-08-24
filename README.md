# Adaptive Order Routing

Adaptive Order Routing is a Master's thesis implementation for evaluating machine-learning-based adaptive routing under steady and bursty workloads.

The system uses Java, Spring Boot, Axon Framework, RabbitMQ, PostgreSQL and Web3j. Order lifecycle events are routed across three equivalent RabbitMQ processing queues using:

- **Shortest Queue** — selects the queue with the smallest ready-message queue length.
- **Little's Law** — estimates waiting time using queue length and observed consumer throughput.
- **ML Routing** — uses XGBoost to predict realised waiting time for each candidate queue and selects the lowest prediction.

A separate round-robin training mode is used to collect supervised-learning data. Random Forest is retained as an offline ML baseline, while XGBoost is exported for Java runtime inference.

Blockchain processing remains in the common consumer path using Ganache and Web3j.

## Author

**amylauren5**

## Project Structure

```text
adaptive-order-routing/
├── order-routing-shared/
├── order-routing-producer/
├── order-routing-consumer/
├── scripts/
│   ├── start.sh
│   ├── teardown.sh
│   ├── setup/
│   │   ├── common.sh
│   │   └── blockchain.sh
│   ├── ml-training/
│   │   ├── run-training.sh
│   │   ├── run-final-training.sh
│   │   ├── build_training_datasets.py
│   │   ├── train_models.py
│   │   └── requirements.txt
│   └── evaluation/
│       ├── run-evaluation.sh
│       ├── run-final-evaluation.sh
│       └── analyse_experiments.py
├── data/
│   ├── training/
│   ├── shortest-queue/
│   ├── little-law/
│   └── ml/
├── docker-compose.yml
└── pom.xml
```

The Maven modules are:

- **`order-routing-shared`** — shared commands, events, messaging types and configuration.
- **`order-routing-producer`** — workload generation, routing, queue-state collection, training observations and XGBoost inference.
- **`order-routing-consumer`** — RabbitMQ consumption, per-order resequencing, blockchain processing, query-side updates and realised outcomes.

Supporting services include Axon Server, RabbitMQ, PostgreSQL and Ganache.

## Prerequisites

The project is intended to run from a Linux or Unix-like shell. On Windows, **WSL2 with Ubuntu 24.04 LTS** can be used.

Required software:

- **OpenJDK 21**
- **Apache Maven**
- **Docker**
- **Docker Compose**
- **Python 3.12**
- **Python virtual-environment support (`python3-venv`)**
- **GNU OpenMP runtime (`libgomp1`)**

On Ubuntu 24.04 / WSL2:

```bash
sudo apt update
sudo apt install \
    openjdk-21-jdk \
    maven \
    python3 \
    python3-venv \
    python3-pip \
    libgomp1
```

Verify the environment:

```bash
java -version
mvn -version
python3 --version
docker --version
docker compose version
```

The Java implementation uses:

- Spring Boot 3.3.3
- Axon Framework 4.10.1
- XGBoost4J 3.3.0
- Web3j 4.12.2
- PostgreSQL JDBC 42.7.12

## Python Environment

Create the project virtual environment from the repository root:

```bash
python3 -m venv .venv
```

Install the required Python packages:

```bash
.venv/bin/python -m pip install --upgrade pip
.venv/bin/pip install -r ./scripts/ml-training/requirements.txt
```

Verify the ML dependencies:

```bash
.venv/bin/python -c \
    "import numpy, pandas, sklearn, xgboost; print('ML dependencies OK')"
```

The training scripts explicitly use:

```text
.venv/bin/python
```

so manual activation of the virtual environment is not required.

## Environment Configuration

The scripts expect:

```text
scripts/.env
```

with the required PostgreSQL, RabbitMQ and blockchain-provider configuration:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD

RABBITMQ_USER
RABBITMQ_PASS

WEB3_PROVIDER
```

During startup, the blockchain setup populates:

```text
WALLET_ADDRESS
PRIVATE_KEY
CONTRACT_ADDRESS
```

Do not commit credentials or generated private keys to Git.

## Build and Test

Build all Maven modules and run the Java tests:

```bash
mvn clean package
```

The build covers:

```text
order-routing-shared
order-routing-producer
order-routing-consumer
```

Tests can also be run separately:

```bash
mvn test
```

The test suite includes Java/XGBoost prediction-parity testing and consumer resequencing tests covering in-order, out-of-order, duplicate and concurrent delivery.

## Running a Single Experiment

The main startup script accepts:

```text
./scripts/start.sh \
    <strategy> \
    <seed> \
    <arrival-scale> \
    <burst-enabled> \
    <burst-multiplier> \
    <burst-duration-seconds> \
    <workload-duration-seconds>
```

Supported modes are:

```text
training
shortest-queue
little-law
ml
```

Example:

```bash
./scripts/start.sh \
    shortest-queue \
    1001 \
    5.0 \
    true \
    0.25 \
    5 \
    60
```

The startup process configures Ganache, PostgreSQL, RabbitMQ and Axon Server before starting the consumer and producer applications.

Reset the environment with:

```bash
./scripts/teardown.sh
```

The automated training and evaluation pipelines perform teardown between runs.

## ML Training

### Single Training Run

Run:

```bash
./scripts/ml-training/run-training.sh \
    <seed> \
    <arrival-scale> \
    <burst-enabled> \
    <burst-multiplier> \
    <burst-duration-seconds> \
    <workload-duration-seconds>
```

Example:

```bash
./scripts/ml-training/run-training.sh \
    2001 \
    5.0 \
    true \
    0.25 \
    5 \
    60
```

Training outputs are written to:

```text
data/training/
```

Each run produces observations and realised outcomes that are joined using the routing-decision UUID. Dataset preprocessing checks duplicate, unmatched and invalid records before model fitting.

### Final Training Pipeline

Run:

```bash
./scripts/ml-training/run-final-training.sh
```

The final training pipeline performs:

```text
5 workload conditions × 5 seeds = 25 runs
```

Training seeds:

```text
2001
2002
2003
2004
2005
```

| Condition | Scale | Burst | Multiplier | Burst duration | Duration |
|---|---:|---|---:|---:|---:|
| Low steady | 8.0 | No | 1.0 | — | 60 s |
| Medium steady | 5.0 | No | 1.0 | — | 60 s |
| High steady | 3.0 | No | 1.0 | — | 60 s |
| Strong short burst | 5.0 | Yes | 0.25 | 5 s | 60 s |
| Moderate long burst | 5.0 | Yes | 0.50 | 10 s | 60 s |

Model development uses workload-run-level train, validation and test separation.

Random Forest is retained as an offline baseline. The selected XGBoost model and feature schema are copied to:

```text
order-routing-producer/src/main/resources/models/
├── xgboost-model.json
└── model-schema.json
```

The training scripts report the wall-clock duration of individual runs and of the complete final training pipeline.

## Final Evaluation

Run:

```bash
./scripts/evaluation/run-final-evaluation.sh
```

The final evaluation performs:

```text
3 workload conditions
× 5 evaluation seeds
× 3 routing strategies
= 45 runs
```

Strategies:

```text
shortest-queue
little-law
ml
```

Evaluation seeds:

```text
1001
1002
1003
1004
1005
```

These are separate from the model-development seeds.

| Condition | Scale | Burst | Multiplier | Burst duration | Duration |
|---|---:|---|---:|---:|---:|
| Medium steady control | 5.0 | No | 1.0 | — | 60 s |
| Strong short burst | 5.0 | Yes | 0.25 | 5 s | 60 s |
| Moderate long burst | 5.0 | Yes | 0.50 | 10 s | 60 s |

Burst-enabled runs begin the burst at **20 seconds**.

Strategy order is rotated between seeds to reduce systematic run-order effects.

Results are written by strategy:

```text
data/
├── shortest-queue/
├── little-law/
└── ml/
```

Each completed run contains:

```text
run_metadata.json
routing_metrics.csv
queue_metrics.csv
event_metrics.csv
```

The evaluation scripts verify these files before accepting a run as complete and report both individual-run and full-matrix script durations.

## Analysing Results

After evaluation completes, run:

```bash
.venv/bin/python \
    scripts/evaluation/analyse_experiments.py \
    --data-dir data
```

The default run-level summary is:

```text
data/evaluation_summary.csv
```

The analysis includes:

- mean, median, p95 and p99 realised waiting time;
- processing time;
- queue backlog and imbalance;
- burst recovery behaviour;
- producer-side routing overhead;
- ML prediction-pipeline time;
- queue-sampling quality.

Runs that do not recover before the experiment ends are retained and marked as non-recovered rather than silently discarded.

## Implementation Notes

RabbitMQ queue state is refreshed periodically into a cached snapshot rather than synchronously queried for every routing decision. The default refresh interval is **1000 ms**.

The ML model uses five pre-routing queue-state features:

```text
queue length
arrival rate
consumer throughput
utilisation
backlog growth
```

The supervised target is realised waiting time from event publication until the successful consumer processing attempt. Events buffered by per-order resequencing therefore include the time spent waiting for a predecessor.

Per-order resequencing is protected against concurrent listener execution, and already-processed sequence numbers are ignored to prevent duplicate lifecycle side effects.

For ML routing, total producer-side routing overhead and ML prediction-pipeline time are recorded separately.

The three processing queues use equivalent consumer settings:

```text
prefetch = 1
concurrency = 1
maximum concurrency = 1
acknowledgement mode = auto
```

## Reproducibility

All final routing strategies should be executed on the same hardware and software environment.

The final thesis records the experimental host specification together with the relevant Java, Python, Docker, RabbitMQ, Axon Server and PostgreSQL versions.

Blockchain processing remains in the common consumer path for all routing strategies so that it contributes consistently to consumer load.

## License

This project is licensed under the MIT License. See `LICENSE` for details.