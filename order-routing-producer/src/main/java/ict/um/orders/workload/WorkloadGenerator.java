package ict.um.orders.workload;

import ict.um.orders.core_api.commands.ApproveOrderCommand;
import ict.um.orders.core_api.commands.CancelOrderCommand;
import ict.um.orders.core_api.commands.CompleteOrderCommand;
import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.core_api.commands.DispatchOrderCommand;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.evaluation.RoutingMetricsCollector;
import ict.um.orders.services.blockchain.DataHashingService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WorkloadGenerator {

    private static final Logger logger = LoggerFactory.getLogger(WorkloadGenerator.class);

    // Dependencies
    private final CommandGateway commandGateway;
    private final DataHashingService dataHashingService;
    private final TaskScheduler taskScheduler;
    private final RoutingMetricsCollector metricsCollector;
    private final JdbcTemplate jdbcTemplate;

    // Workload configuration
    private final long randomSeed;
    private final double arrivalScale;
    private final long workloadDurationMillis;

    // Burst configuration
    private final boolean burstEnabled;
    private final double burstMultiplier;
    private final long burstStartMillis;
    private final long burstDurationMillis;

    // Experiment state
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AtomicBoolean experimentCompleted = new AtomicBoolean(false);

    private final AtomicInteger generatedOrderCount = new AtomicInteger();

    private final AtomicInteger activeLifecycleCount = new AtomicInteger();

    private volatile long workloadStartedAt;
    private volatile long generationStoppedAt;
    private volatile WorkloadPhase lastLoggedPhase;

    private volatile ScheduledFuture<?> nextOrderTask;
    private volatile ScheduledFuture<?> drainCheckTask;

    public WorkloadGenerator(
            CommandGateway commandGateway,
            DataHashingService dataHashingService,
            TaskScheduler taskScheduler,
            RoutingMetricsCollector metricsCollector,
            JdbcTemplate jdbcTemplate,
            @Value("${workload.random-seed}") long randomSeed,
            @Value("${workload.arrival-scale}") double arrivalScale,
            @Value("${workload.duration-seconds}") long durationSeconds,
            @Value("${workload.burst-enabled:false}") boolean burstEnabled,
            @Value("${workload.burst-start-seconds:20}") long burstStartSeconds,
            @Value("${workload.burst-duration-seconds:10}") long burstDurationSeconds,
            @Value("${workload.burst-multiplier:0.25}") double burstMultiplier
    ) {
        if (!Double.isFinite(arrivalScale)
                || arrivalScale <= 0.0) {
            throw new IllegalArgumentException(
                    "Workload arrival scale must be positive"
            );
        }

        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException(
                    "Workload duration must be positive"
            );
        }

        if (burstEnabled) {
            if (!Double.isFinite(burstMultiplier)
                    || burstMultiplier <= 0.0
                    || burstMultiplier > 1.0) {
                throw new IllegalArgumentException(
                        "Burst multiplier must be in (0, 1]."
                );
            }

            if (burstStartSeconds < 0L) {
                throw new IllegalArgumentException(
                        "Burst start must not be negative."
                );
            }

            if (burstDurationSeconds <= 0L) {
                throw new IllegalArgumentException(
                        "Burst duration must be positive."
                );
            }

            if (burstStartSeconds + burstDurationSeconds
                    > durationSeconds) {
                throw new IllegalArgumentException(
                        "Burst interval must fit within workload duration."
                );
            }
        }

        this.commandGateway = commandGateway;
        this.dataHashingService = dataHashingService;
        this.taskScheduler = taskScheduler;
        this.metricsCollector = metricsCollector;
        this.jdbcTemplate = jdbcTemplate;
        this.workloadDurationMillis = TimeUnit.SECONDS.toMillis(durationSeconds);
        this.burstEnabled = burstEnabled;
        this.burstMultiplier = burstMultiplier;
        this.burstStartMillis = TimeUnit.SECONDS.toMillis(burstStartSeconds);
        this.burstDurationMillis = TimeUnit.SECONDS.toMillis(burstDurationSeconds);
        this.randomSeed = randomSeed;
        this.arrivalScale = arrivalScale;

        OlistSampling.setSeed(randomSeed);
        OlistSampling.setArrivalScale(arrivalScale);
    }

    // ----------------------- Experiment startup -----------------------
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        workloadStartedAt = System.currentTimeMillis();

        logger.info(
                "\n==================================================\n"
                        + "Workload generation started\n"
                        + "Duration          : {} seconds\n"
                        + "Random seed       : {}\n"
                        + "Arrival scale     : {}\n"
                        + "Burst enabled     : {}\n"
                        + "Burst start       : {} seconds\n"
                        + "Burst duration    : {} seconds\n"
                        + "Burst multiplier  : {}\n"
                        + "==================================================",
                TimeUnit.MILLISECONDS.toSeconds(workloadDurationMillis),
                randomSeed,
                arrivalScale,
                burstEnabled,
                TimeUnit.MILLISECONDS.toSeconds(burstStartMillis),
                TimeUnit.MILLISECONDS.toSeconds(burstDurationMillis),
                burstMultiplier
        );

        taskScheduler.schedule(
                this::stopGeneratingNewOrders,
                Instant.now().plusMillis(workloadDurationMillis)
        );

        scheduleNextOrder();
    }

    // ----------------------- Workload generation -----------------------
    private void scheduleNextOrder() {
        if (!running.get()) {
            return;
        }

        logPhaseTransition();

        double multiplier =
                isBurstActive() ? burstMultiplier : 1.0;

        long delayMillis =
                OlistSampling.sampleInterArrival(multiplier);

        nextOrderTask = taskScheduler.schedule(
                this::generateAndReschedule,
                Instant.now().plusMillis(delayMillis)
        );
    }

    private void generateAndReschedule() {
        if (!running.get()) {
            return;
        }

        try {
            generateOrder();
        } catch (Exception exception) {
            logger.error(
                    "Failed to generate order",
                    exception
            );
        } finally {
            if (running.get()) {
                scheduleNextOrder();
            }
        }
    }

    private boolean isBurstActive() {
        if (!burstEnabled) {
            return false;
        }

        long elapsedMillis =
                System.currentTimeMillis() - workloadStartedAt;

        long burstEndMillis =
                burstStartMillis + burstDurationMillis;

        return elapsedMillis >= burstStartMillis
                && elapsedMillis < burstEndMillis;
    }

    private WorkloadPhase currentPhase() {
        if (!burstEnabled) {
            return WorkloadPhase.BASELINE;
        }

        long elapsedMillis =
                System.currentTimeMillis() - workloadStartedAt;

        if (elapsedMillis < burstStartMillis) {
            return WorkloadPhase.PRE_BURST;
        }

        if (elapsedMillis
                < burstStartMillis + burstDurationMillis) {
            return WorkloadPhase.BURST;
        }

        return WorkloadPhase.POST_BURST;
    }

    private void logPhaseTransition() {
        WorkloadPhase phase = currentPhase();

        if (phase == lastLoggedPhase) {
            return;
        }

        lastLoggedPhase = phase;

        logger.info(
                "Workload phase changed: phase={}, elapsedMs={}",
                phase,
                System.currentTimeMillis() - workloadStartedAt
        );
    }

    // ----------------------- Order generation -----------------------
    private void generateOrder() {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        int itemCount = OlistSampling.sampleItemCount();

        long createdAt = System.currentTimeMillis();

        CreateOrderCommand commandWithoutHash =
                new CreateOrderCommand(
                        orderId,
                        customerId,
                        OlistSampling.sampleCategory(),
                        OlistSampling.sampleOrderValue(),
                        itemCount,
                        createdAt,
                        ""
                );

        String dataHash =
                dataHashingService.computeInitialDataHash(
                        commandWithoutHash
                );

        CreateOrderCommand createCommand =
                new CreateOrderCommand(
                        orderId,
                        customerId,
                        commandWithoutHash.getCategory(),
                        commandWithoutHash.getOrderValue(),
                        itemCount,
                        createdAt,
                        dataHash
                );

        int generatedCount =
                generatedOrderCount.incrementAndGet();

        logger.debug(
                "Generating order {}; total generated={}",
                orderId,
                generatedCount
        );

        commandGateway.send(createCommand)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        logger.error(
                                "Failed to create order {}",
                                orderId,
                                throwable
                        );
                        return;
                    }

                    activeLifecycleCount.incrementAndGet();
                    scheduleRemainingLifecycle(orderId);
                });
    }

    // ----------------------- Order lifecycle -----------------------
    private void scheduleRemainingLifecycle(String orderId) {
        long approvalDelay =
                OlistSampling.sampleApprovalDelay();

        if (OlistSampling.sampleCancellation()) {
            scheduleCancellation(
                    orderId,
                    approvalDelay
            );
            return;
        }

        long dispatchDelay =
                OlistSampling.sampleDispatchDelay();

        long deliveryDelay =
                OlistSampling.sampleDeliveryDelay();

        CompletableFuture.delayedExecutor(
                approvalDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new ApproveOrderCommand(
                                orderId,
                                System.currentTimeMillis()
                        )
                ).whenComplete((approvalResult, approvalError) -> {
                    if (approvalError != null) {
                        logger.error(
                                "Failed to approve order {}",
                                orderId,
                                approvalError
                        );

                        activeLifecycleCount.decrementAndGet();
                        return;
                    }

                    scheduleDispatch(
                            orderId,
                            dispatchDelay,
                            deliveryDelay
                    );
                })
        );
    }

    private void scheduleDispatch(
            String orderId,
            long dispatchDelay,
            long deliveryDelay
    ) {
        CompletableFuture.delayedExecutor(
                dispatchDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new DispatchOrderCommand(
                                orderId,
                                System.currentTimeMillis()
                        )
                ).whenComplete((dispatchResult, dispatchError) -> {
                    if (dispatchError != null) {
                        logger.error(
                                "Failed to dispatch order {}",
                                orderId,
                                dispatchError
                        );

                        activeLifecycleCount.decrementAndGet();
                        return;
                    }

                    scheduleCompletion(
                            orderId,
                            deliveryDelay
                    );
                })
        );
    }

    private void scheduleCompletion(
            String orderId,
            long deliveryDelay
    ) {
        CompletableFuture.delayedExecutor(
                deliveryDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new CompleteOrderCommand(
                                orderId,
                                System.currentTimeMillis()
                        )
                ).whenComplete((completionResult, completionError) -> {
                    try {
                        if (completionError != null) {
                            logger.error(
                                    "Failed to complete order {}",
                                    orderId,
                                    completionError
                            );
                        }
                    } finally {
                        activeLifecycleCount.decrementAndGet();
                    }
                })
        );
    }

    private void scheduleCancellation(
            String orderId,
            long approvalDelay
    ) {
        long cancellationDelay =
                Math.max(1L, approvalDelay / 2L);

        CompletableFuture.delayedExecutor(
                cancellationDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new CancelOrderCommand(
                                orderId,
                                System.currentTimeMillis(),
                                OlistSampling.sampleCancellationReason()
                        )
                ).whenComplete((result, error) -> {
                    try {
                        if (error != null) {
                            logger.error(
                                    "Failed to cancel order {}",
                                    orderId,
                                    error
                            );
                        }
                    } finally {
                        activeLifecycleCount.decrementAndGet();
                    }
                })
        );
    }

    // ----------------------- Generation drain -----------------------
    private void stopGeneratingNewOrders() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        generationStoppedAt = System.currentTimeMillis();

        ScheduledFuture<?> task = nextOrderTask;

        if (task != null) {
            task.cancel(false);
            nextOrderTask = null;
        }

        logger.info(
                "Workload generation period completed; "
                        + "no new orders will be created. "
                        + "Waiting for the system to drain."
        );

        waitForExperimentCompletion();
    }

    private void waitForExperimentCompletion() {
        if (drainCheckTask != null
                && !drainCheckTask.isDone()) {
            return;
        }

        drainCheckTask =
                taskScheduler.scheduleWithFixedDelay(
                        this::checkExperimentCompletion,
                        Duration.ofSeconds(2)
                );
    }

    private void checkExperimentCompletion() {
        try {
            ExperimentState state = readExperimentState();

            logger.info(
                    "Drain status: activeLifecycles={}, "
                            + "queueBacklog={}, pendingEvents={}",
                    state.activeLifecycles(),
                    state.queueBacklog(),
                    state.pendingEvents()
            );

            if (!state.isDrained()) {
                return;
            }

            if (!experimentCompleted.compareAndSet(false, true)) {
                return;
            }

            ScheduledFuture<?> task = drainCheckTask;

            if (task != null) {
                task.cancel(false);
                drainCheckTask = null;
            }

            long experimentCompletedAt =
                    System.currentTimeMillis();

            long totalElapsedMillis =
                    experimentCompletedAt - workloadStartedAt;

            long drainDurationMillis =
                    experimentCompletedAt - generationStoppedAt;

            logger.info(
                    "\n==================================================\n"
                            + "Experiment completed successfully\n"
                            + "Generated orders  : {}\n"
                            + "Generation period : {} ms ({} seconds)\n"
                            + "Drain period      : {} ms ({} seconds)\n"
                            + "Total duration    : {} ms ({} seconds)\n"
                            + "Active lifecycles : {}\n"
                            + "Pending events    : {}\n"
                            + "Queue backlog     : {}\n"
                            + "==================================================",
                    generatedOrderCount.get(),
                    workloadDurationMillis,
                    TimeUnit.MILLISECONDS.toSeconds(
                            workloadDurationMillis
                    ),
                    drainDurationMillis,
                    TimeUnit.MILLISECONDS.toSeconds(
                            drainDurationMillis
                    ),
                    totalElapsedMillis,
                    TimeUnit.MILLISECONDS.toSeconds(
                            totalElapsedMillis
                    ),
                    state.activeLifecycles(),
                    state.pendingEvents(),
                    state.queueBacklog()
            );

        } catch (Exception exception) {
            logger.error(
                    "Failed to check whether the experiment has drained",
                    exception
            );
        }
    }

    private ExperimentState readExperimentState() {
        RoutingFeatures features =
                metricsCollector.collectAll();

        long queueBacklog = 0L;

        for (String queueKey : RoutingFeatures.QUEUE_ORDER) {
            QueueFeatures queueFeatures =
                    features.queues().get(queueKey);

            if (queueFeatures == null) {
                throw new IllegalStateException(
                        "Missing metrics for queue: " + queueKey
                );
            }

            queueBacklog += Math.max(
                    0L,
                    Math.round(queueFeatures.queueLength())
            );
        }

        Long pendingEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pending_orders_view",
                Long.class
        );

        return new ExperimentState(
                activeLifecycleCount.get(),
                queueBacklog,
                pendingEvents == null ? 0L : pendingEvents
        );
    }

    //  ----------------------- Getters -----------------------
    public long getWorkloadStartedAt() {
        return workloadStartedAt;
    }

    public String getCurrentPhaseName() {
        return currentPhase().name();
    }

    // ----------------------- Nested types -----------------------
    private enum WorkloadPhase {
        BASELINE,
        PRE_BURST,
        BURST,
        POST_BURST
    }

    private record ExperimentState(
            int activeLifecycles,
            long queueBacklog,
            long pendingEvents
    ) {
        private boolean isDrained() {
            return activeLifecycles == 0
                    && queueBacklog == 0L
                    && pendingEvents == 0L;
        }
    }
}