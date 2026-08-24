# Adaptive Order Routing

Adaptive Order Routing is a Master's thesis implementation for evaluating machine-learning-based adaptive routing under steady and bursty workloads.

The system uses an event-driven CQRS architecture built with Java, Spring Boot, Axon Framework, RabbitMQ, PostgreSQL and Web3j. Order lifecycle events are routed across three equivalent RabbitMQ processing queues using one of three runtime routing strategies:

- **Shortest Queue** — selects the queue with the smallest observed ready-message queue length.
- **Little's Law** — selects a queue using queue length and observed consumer throughput.
- **ML Routing** — uses an XGBoost regression model to predict realised waiting time for each candidate queue and selects the queue with the lowest predicted waiting time.

A separate **training routing mode** uses round-robin queue assignment to collect supervised-learning observations without using the adaptive ML router. Random Forest is retained as an offline ML baseline during model development, while XGBoost is exported for Java runtime inference.

The project also retains blockchain processing in the consumer path. A local Ganache instance and the order lifecycle smart contract are configured automatically by the startup scripts.

## Author

**amylauren5**

## Architecture

The project is divided into three Maven modules:

- **`order-routing-shared/`**  
  Contains shared commands, events, messaging types and common configuration used by the producer and consumer.

- **`order-routing-producer/`**  
  Contains the command-side application, synthetic workload generation, RabbitMQ routing, queue-state collection, training observation collection and XGBoost runtime inference.

- **`order-routing-consumer/`**  
  Consumes routed events from the three RabbitMQ processing queues, performs per-order resequencing, executes blockchain operations, updates query-side state and records realised event outcomes.

Supporting infrastructure includes:

- **Axon Server** — command and event infrastructure.
- **RabbitMQ** — three equivalent processing queues and queue-state metrics.
- **PostgreSQL** — query-side and experiment state.
- **Ganache** — local Ethereum-compatible blockchain used by the consumer.
- **Docker Compose** — infrastructure and application orchestration.

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
│   │   └── train_models.py
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

## Prerequisites

The project is intended to be run from a Linux or Unix-like shell. On Windows, WSL2 can be used.

Required software:

- **Java 21**
- **Apache Maven**
- **Docker**
- **Docker Compose**
- **Python 3**
- **Python virtual environment** for the final ML training pipeline

The current Java implementation uses:

- Spring Boot 3.3.3
- Axon Framework 4.10.1
- XGBoost4J 3.3.0
- Web3j 4.12.2
- PostgreSQL JDBC 42.7.12

Docker Compose provides PostgreSQL, RabbitMQ and Axon Server. Ganache is started separately by the blockchain setup script.

The exact hardware and software environment used for the final thesis experiments should be recorded alongside the experimental results.

## Environment Configuration

The scripts expect:

```text
scripts/.env
```

The environment file must contain the required PostgreSQL, RabbitMQ and blockchain-provider configuration:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD

RABBITMQ_USER
RABBITMQ_PASS

WEB3_PROVIDER
```

During startup, the blockchain setup automatically obtains and updates:

```text
WALLET_ADDRESS
PRIVATE_KEY
CONTRACT_ADDRESS
```

using the local Ganache instance and deployed contract.

Private credentials and generated private keys should not be committed to Git.

## Build and Test

From the repository root, build all Maven modules and run the test suite with:

```bash
mvn clean package
```

This builds:

```text
order-routing-shared
order-routing-producer
order-routing-consumer
```

The producer and consumer Dockerfiles expect the Maven-generated JAR files under their respective `target/` directories, so the Maven build should be completed before starting an experiment.

Tests can also be run independently with:

```bash
mvn test
```

The project includes tests for Java/XGBoost prediction parity and consumer-side resequencing correctness.

## Starting a Single Run

The main startup script is:

```bash
./scripts/start.sh
```

Its arguments are:

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

Supported routing modes are:

```text
training
shortest-queue
little-law
ml
```

For example:

```bash
./scripts/start.sh shortest-queue 1001 5.0 true 0.25 5 60
```

The startup script automatically:

1. creates the experiment Docker network if required;
2. starts Ganache;
3. obtains a Ganache account and private key;
4. builds and deploys the order lifecycle smart contract;
5. starts PostgreSQL and RabbitMQ;
6. waits for PostgreSQL and RabbitMQ to become ready;
7. starts Axon Server and waits for it to become ready;
8. builds and starts the consumer;
9. waits for all three RabbitMQ processing queues to be declared;
10. builds and starts the producer.

The synthetic workload then executes automatically using the supplied experiment configuration.

## Stopping and Resetting the System

Run:

```bash
./scripts/teardown.sh
```

The teardown script stops the producer and consumer, resets experiment database state, removes Ganache and the remaining experiment containers, and deletes the experiment Docker network.

The automated training and evaluation scripts invoke teardown between runs to reduce carry-over effects between experiments and provide a consistent starting state.

## Routing Strategies

### Shortest Queue

The shortest-queue baseline selects the processing queue with the smallest observed ready-message queue length.

It provides a deterministic baseline that reacts directly to observed queue occupancy without using a predictive model.

### Little's Law

The Little's-Law-based strategy estimates queueing conditions using observed queue length and consumer throughput and selects the queue with the lowest estimated waiting time.

It provides an independent deterministic routing baseline for comparison with ML routing.

### ML Routing

The adaptive ML strategy evaluates all three candidate queues using an XGBoost regression model.

For each candidate queue, the router constructs the corresponding pre-routing feature vector and predicts realised waiting time. The queue with the minimum predicted waiting time is selected.

The runtime model and its feature schema are loaded from:

```text
order-routing-producer/src/main/resources/models/
├── xgboost-model.json
└── model-schema.json
```

The schema is retained alongside the model to preserve feature ordering between Python model development and Java runtime inference.

## Queue-State Features and Sampling

The routing system uses five queue-state features:

```text
queue length
arrival rate
consumer throughput
utilisation
backlog growth
```

All candidate queue features used for ML training and runtime inference are based on observations made before the corresponding routing decision.

RabbitMQ queue state is periodically refreshed and stored as a cached routing snapshot rather than synchronously querying the RabbitMQ management API for every routing decision.

The default queue-state refresh interval is:

```text
1000 ms
```

Routing therefore normally uses the most recently completed queue-state snapshot. Queue-state measurements may consequently have some age relative to the routing decision, in addition to the sampling behaviour of RabbitMQ's management metrics.

Evaluation queue metrics are sampled separately at a configurable interval.

Both the routing snapshot refresh interval and evaluation sampling interval are recorded in each experiment's metadata.

## RabbitMQ Consumer Configuration

The three processing queues are intended to be equivalent and use the same consumer configuration.

The processing listeners explicitly configure:

```text
prefetch = 1
concurrency = 1
maximum concurrency = 1
acknowledgement mode = auto
```

Keeping the consumer configuration equivalent across the three queues ensures that routing comparisons are based on queue selection rather than intentionally different queue-processing capacities.

## Per-Order Resequencing

Order lifecycle events may be routed to different RabbitMQ processing queues. Because the queue listeners can execute independently, events belonging to the same order may be delivered out of sequence.

The consumer therefore implements per-order resequencing.

Future events are buffered until their expected predecessor has been processed. Resequencing operations for an individual order are protected using a per-order lock so that sequence checking, buffering, lifecycle-state updates and release of buffered successors are serialised for that aggregate.

Already-processed sequence numbers are ignored to prevent duplicate/redelivered events from repeating lifecycle side effects.

The resequencing tests cover:

- in-order delivery;
- deliberately out-of-order delivery;
- multiple buffered successors;
- duplicate/redelivered events;
- concurrent listener execution.

## Realised Waiting-Time Target

The supervised-learning target is the realised waiting time between event publication and the start of the listener processing attempt that ultimately succeeds in passing consumer-side resequencing and processing the event.

If an event is delivered out of order and buffered while waiting for a predecessor, the initial buffered processing attempt does not generate a training outcome. When the event is subsequently released for processing, the successful processing attempt provides the timestamp used for the realised waiting-time outcome.

Consequently, delay caused by waiting for a predecessor during resequencing is represented in the realised waiting-time target.

Training observations and realised outcomes are associated using a routing-decision UUID.

## ML Training

### Single Training Run

A single training-data collection run can be executed with:

```bash
./scripts/ml-training/run-training.sh \
    <seed> \
    <arrival-scale> \
    <burst-enabled> \
    <burst-multiplier> \
    <burst-duration-seconds> \
    <workload-duration-seconds>
```

For example:

```bash
./scripts/ml-training/run-training.sh \
    2001 5.0 true 0.25 5 60
```

Training runs are written under:

```text
data/training/
```

The script waits for workload completion and then builds the joined training dataset for that run.

### Final Training Pipeline

The complete training pipeline is:

```bash
./scripts/ml-training/run-final-training.sh
```

This performs **25 training runs**:

```text
5 workload conditions × 5 training seeds
```

using the following training seeds:

```text
2001
2002
2003
2004
2005
```

The five training workload conditions are:

| Condition | Arrival scale | Burst | Multiplier | Burst duration | Duration |
|---|---:|---|---:|---:|---:|
| Low steady | 8.0 | No | 1.0 | — | 60 s |
| Medium steady | 5.0 | No | 1.0 | — | 60 s |
| High steady | 3.0 | No | 1.0 | — | 60 s |
| Strong short burst | 5.0 | Yes | 0.25 | 5 s | 60 s |
| Moderate long burst | 5.0 | Yes | 0.50 | 10 s | 60 s |

After data collection, the script trains the Random Forest and XGBoost models using:

```text
scripts/ml-training/train_models.py
```

Model development uses workload-run-level train, validation and test separation to reduce leakage between observations generated by the same workload run.

Random Forest is retained as an offline ML comparator. XGBoost is the model exported for runtime adaptive routing.

The final runtime artifacts are copied to:

```text
order-routing-producer/src/main/resources/models/
├── xgboost-model.json
└── model-schema.json
```

The final training script expects a Python virtual environment at:

```text
.venv/
```

and invokes:

```text
.venv/bin/python
```

## Training-Data Integrity

Training observations and realised consumer outcomes are written separately and subsequently associated using the routing-decision UUID.

Dataset preprocessing validates the resulting associations before model fitting, including checks for:

- duplicated observation identifiers;
- duplicated outcome identifiers;
- unmatched observations;
- unmatched outcomes;
- invalid one-to-one associations;
- selected-queue mismatches;
- missing or non-finite features;
- missing, negative or non-finite target values.

Integrity statistics are reported during dataset construction. Invalid data is not silently accepted into the model-development dataset.

## Final Routing Evaluation

The complete end-to-end routing evaluation is executed with:

```bash
./scripts/evaluation/run-final-evaluation.sh
```

The final evaluation matrix contains:

```text
3 workload conditions
× 5 evaluation seeds
× 3 routing strategies
= 45 runs
```

The evaluated routing strategies are:

```text
shortest-queue
little-law
ml
```

Evaluation seeds are:

```text
1001
1002
1003
1004
1005
```

These seeds are deliberately separate from the model-development seeds.

This separates predictive-model development and testing from the final end-to-end routing comparison.

The final evaluation workload conditions are:

| Condition | Arrival scale | Burst | Multiplier | Burst duration | Duration |
|---|---:|---|---:|---:|---:|
| Medium steady control | 5.0 | No | 1.0 | — | 60 s |
| Strong short burst | 5.0 | Yes | 0.25 | 5 s | 60 s |
| Moderate long burst | 5.0 | Yes | 0.50 | 10 s | 60 s |

Burst-enabled experiments use a burst start of:

```text
20 seconds
```

Strategy execution order is rotated between seeds to reduce systematic run-order effects.

Completed experiment data is organised automatically by strategy:

```text
data/
├── shortest-queue/
├── little-law/
└── ml/
```

Each completed run directory contains:

```text
run_metadata.json
routing_metrics.csv
queue_metrics.csv
event_metrics.csv
```

The final-evaluation script verifies that the expected output files exist and are non-empty before accepting a run as complete. It refuses to silently overwrite or skip an existing incomplete run directory.

## Evaluation Instrumentation

The evaluation records producer-side routing metrics, consumer event metrics and periodic queue-state samples.

### Routing Overhead

The complete producer-side queue-selection cost is recorded as:

```text
routing_overhead_ns
```

For ML routing, the time spent executing the prediction pipeline for the three candidate queues is additionally recorded as:

```text
model_inference_ns
```

This allows the ML prediction cost to be distinguished from the complete producer-side routing-service cost.

For deterministic routing strategies, the model-inference value is zero.

### Queue Metrics

Periodic queue-state samples are written to:

```text
queue_metrics.csv
```

These samples support analysis of queue backlog, queue imbalance, burst behaviour, recovery behaviour and sampling quality.

### Event Metrics

Consumer-side event outcomes are written to:

```text
event_metrics.csv
```

These records include the timestamps required to derive realised waiting time and processing time.

## Analysing Experiments

After the evaluation runs have completed, run:

```bash
python3 scripts/evaluation/analyse_experiments.py --data-dir data
```

If `--data-dir` is omitted, the analyser defaults to:

```text
data/
```

The run-level evaluation summary is written by default to:

```text
data/evaluation_summary.csv
```

The analysis derives run-level metrics including:

- mean, median, p95 and p99 queueing latency;
- processing time;
- aggregate backlog;
- queue imbalance;
- burst recovery behaviour;
- producer-side routing overhead;
- ML prediction-pipeline time;
- queue-sampling quality.

Burst runs that do not return to the defined pre-burst recovery threshold before the recorded experiment ends are retained as non-recovered runs rather than silently discarded.

Recovery time should therefore be interpreted together with whether recovery was observed within the experiment window.

## Experimental Data

Training and final evaluation outputs are intentionally separated:

```text
data/
├── training/
├── shortest-queue/
├── little-law/
└── ml/
```

Run identifiers encode the routing strategy and workload configuration so that experiment outputs can be traced back to their generating conditions.

The final evaluation uses fresh seeds that are not used for model fitting, validation or predictive-model testing.

## Experimental Reproducibility

Each experiment records run metadata describing the relevant workload and instrumentation configuration.

The final thesis should additionally report the concrete experimental host and software environment, including:

- processor;
- installed memory;
- operating system;
- WSL version, where applicable;
- Docker and Docker Compose versions;
- Java version;
- Maven version;
- Python version;
- Python XGBoost version;
- RabbitMQ version;
- Axon Server version;
- PostgreSQL version.

All routing strategies in the final comparison should be executed on the same experimental host and software environment.

Blockchain processing remains in the common consumer path for all routing strategies so that it contributes consistently to consumer load across the routing comparison.

## Tests

Build the complete project and execute the test suite with:

```bash
mvn clean package
```

Tests can also be executed without packaging using:

```bash
mvn test
```

The test suite includes Java/XGBoost prediction-parity testing and consumer-side resequencing tests.

The resequencing tests cover:

- in-order delivery;
- deliberately out-of-order delivery;
- multiple buffered successors;
- duplicate/redelivered events;
- concurrent delivery through separate listener threads.

## License

This project is licensed under the MIT License. See `LICENSE` for details.