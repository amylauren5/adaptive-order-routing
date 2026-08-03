package ict.um.orders.workload;

import ict.um.orders.core_api.commands.ApproveOrderCommand;
import ict.um.orders.core_api.commands.CancelOrderCommand;
import ict.um.orders.core_api.commands.CompleteOrderCommand;
import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.core_api.commands.DispatchOrderCommand;
import ict.um.orders.ml.features.QueueFeatures;
import ict.um.orders.ml.features.RoutingFeatures;
import ict.um.orders.ml.metrics.RoutingMetricsCollector;
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

    private static final Logger logger =
            LoggerFactory.getLogger(WorkloadGenerator.class);

    private static final double CANCELLATION_RATE = 0.006285;

    private static final String[] CANCELLATION_REASONS = {
            "customer_request",
            "payment_issue",
            "inventory_unavailable",
            "fraud_suspected"
    };

    private final CommandGateway commandGateway;
    private final DataHashingService dataHashingService;
    private final TaskScheduler taskScheduler;
    private final RoutingMetricsCollector metricsCollector;
    private final JdbcTemplate jdbcTemplate;

    private final long workloadDurationMillis;

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private final AtomicBoolean experimentCompleted =
            new AtomicBoolean(false);

    private final AtomicInteger generatedOrderCount =
            new AtomicInteger();

    /*
     * Counts successfully created orders whose remaining lifecycle
     * has not yet reached completion, cancellation, or failure.
     */
    private final AtomicInteger activeLifecycleCount =
            new AtomicInteger();

    private volatile long workloadStartedAt;

    private volatile ScheduledFuture<?> nextOrderTask;
    private volatile ScheduledFuture<?> drainCheckTask;

    public WorkloadGenerator(
            CommandGateway commandGateway,
            DataHashingService dataHashingService,
            TaskScheduler taskScheduler,
            RoutingMetricsCollector metricsCollector,
            JdbcTemplate jdbcTemplate,
            @Value("${workload.arrival-scale}") double arrivalScale,
            @Value("${workload.duration-seconds}") long durationSeconds
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

        this.commandGateway = commandGateway;
        this.dataHashingService = dataHashingService;
        this.taskScheduler = taskScheduler;
        this.metricsCollector = metricsCollector;
        this.jdbcTemplate = jdbcTemplate;
        this.workloadDurationMillis =
                TimeUnit.SECONDS.toMillis(durationSeconds);

        OlistSampling.setArrivalScale(arrivalScale);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        workloadStartedAt = System.currentTimeMillis();

        logger.info(
                "\n==================================================\n"
                        + "Workload generation started\n"
                        + "Generation duration : {} seconds\n"
                        + "==================================================",
                TimeUnit.MILLISECONDS.toSeconds(
                        workloadDurationMillis
                )
        );

        taskScheduler.schedule(
                this::stopGeneratingNewOrders,
                Instant.now().plusMillis(workloadDurationMillis)
        );

        scheduleNextOrder();
    }

    private void scheduleNextOrder() {
        if (!running.get()) {
            return;
        }

        long delayMillis =
                OlistSampling.sampleInterArrival();

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

    private void stopGeneratingNewOrders() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

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

            long elapsedMillis =
                    System.currentTimeMillis() - workloadStartedAt;

            logger.info(
                    "\n==================================================\n"
                            + "Experiment completed successfully\n"
                            + "Generated orders  : {}\n"
                            + "Generation window : {} seconds\n"
                            + "Total elapsed time: {} ms\n"
                            + "Active lifecycles : {}\n"
                            + "Pending events    : {}\n"
                            + "Queue backlog     : {}\n"
                            + "==================================================",
                    generatedOrderCount.get(),
                    TimeUnit.MILLISECONDS.toSeconds(
                            workloadDurationMillis
                    ),
                    elapsedMillis,
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

    /*
     * Creates one order after each sampled and scaled
     * Olist-derived inter-arrival interval.
     */
    private void generateOrder() {
        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        int itemCount =
                1 + OlistSampling.random().nextInt(5);

        long createdAt =
                System.currentTimeMillis();

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

    private void scheduleRemainingLifecycle(String orderId) {
        long approvalDelay =
                OlistSampling.sampleApprovalDelay();

        if (OlistSampling.random().nextDouble()
                < CANCELLATION_RATE) {
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
                                sampleCancellationReason()
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

    private String sampleCancellationReason() {
        return CANCELLATION_REASONS[
                OlistSampling.random()
                        .nextInt(CANCELLATION_REASONS.length)
                ];
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