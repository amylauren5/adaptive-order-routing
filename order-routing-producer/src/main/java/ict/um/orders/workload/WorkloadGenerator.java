package ict.um.orders.workload;

import ict.um.orders.core_api.commands.ApproveOrderCommand;
import ict.um.orders.core_api.commands.CancelOrderCommand;
import ict.um.orders.core_api.commands.CompleteOrderCommand;
import ict.um.orders.core_api.commands.CreateOrderCommand;
import ict.um.orders.core_api.commands.DispatchOrderCommand;
import ict.um.orders.services.blockchain.DataHashingService;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class WorkloadGenerator {

    private static final Logger logger =
            LoggerFactory.getLogger(WorkloadGenerator.class);

    // Olist canceled orders: 625 / total orders
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

    public WorkloadGenerator(
            CommandGateway commandGateway,
            DataHashingService dataHashingService,
            TaskScheduler taskScheduler,
            @Value("${workload.arrival-scale:1.0}") double arrivalScale
    ) {
        this.commandGateway = commandGateway;
        this.dataHashingService = dataHashingService;
        this.taskScheduler = taskScheduler;

        OlistSampling.setArrivalScale(arrivalScale);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduleNextOrder();
    }

    private void scheduleNextOrder() {
        long delayMillis = OlistSampling.sampleInterArrival();

        taskScheduler.schedule(
                this::generateAndReschedule,
                Instant.now().plusMillis(delayMillis)
        );
    }

    private void generateAndReschedule() {
        try {
            generateOrder();
        } catch (Exception exception) {
            logger.error("Failed to generate order", exception);
        } finally {
            scheduleNextOrder();
        }
    }

    /*
     * Creates one order after each sampled and scaled
     * Olist-derived inter-arrival interval.
     */
    private void generateOrder() {

        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        int itemCount = 1 + OlistSampling.random().nextInt(5);
        long createdAt = System.currentTimeMillis();

        CreateOrderCommand commandWithoutHash = new CreateOrderCommand(
                orderId,
                customerId,
                OlistSampling.sampleCategory(),
                OlistSampling.sampleOrderValue(),
                itemCount,
                createdAt,
                1,
                ""
        );

        String dataHash =
                dataHashingService.computeInitialDataHash(commandWithoutHash);

        CreateOrderCommand createCommand = new CreateOrderCommand(
                orderId,
                customerId,
                commandWithoutHash.getCategory(),
                commandWithoutHash.getOrderValue(),
                itemCount,
                createdAt,
                1,
                dataHash
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

                    scheduleRemainingLifecycle(orderId);
                });
    }

    private void scheduleRemainingLifecycle(String orderId) {

        long approvalDelay = OlistSampling.sampleApprovalDelay();

        if (OlistSampling.random().nextDouble() < CANCELLATION_RATE) {
            scheduleCancellation(orderId, approvalDelay);
            return;
        }

        long dispatchDelay = OlistSampling.sampleDispatchDelay();
        long deliveryDelay = OlistSampling.sampleDeliveryDelay();

        CompletableFuture.delayedExecutor(
                approvalDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new ApproveOrderCommand(
                                orderId,
                                System.currentTimeMillis(),
                                1
                        )
                ).whenComplete((approvalResult, approvalError) -> {
                    if (approvalError != null) {
                        logger.error(
                                "Failed to approve order {}",
                                orderId,
                                approvalError
                        );
                        return;
                    }

                    scheduleDispatch(orderId, dispatchDelay, deliveryDelay);
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
                                System.currentTimeMillis(),
                                2
                        )
                ).whenComplete((dispatchResult, dispatchError) -> {
                    if (dispatchError != null) {
                        logger.error(
                                "Failed to dispatch order {}",
                                orderId,
                                dispatchError
                        );
                        return;
                    }

                    scheduleCompletion(orderId, deliveryDelay);
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
                                System.currentTimeMillis(),
                                3
                        )
                ).whenComplete((completionResult, completionError) -> {
                    if (completionError != null) {
                        logger.error(
                                "Failed to complete order {}",
                                orderId,
                                completionError
                        );
                    }
                })
        );
    }

    private void scheduleCancellation(
            String orderId,
            long approvalDelay
    ) {
        long cancellationDelay = Math.max(1L, approvalDelay / 2L);

        CompletableFuture.delayedExecutor(
                cancellationDelay,
                TimeUnit.MILLISECONDS
        ).execute(() ->
                commandGateway.send(
                        new CancelOrderCommand(
                                orderId,
                                System.currentTimeMillis(),
                                1,
                                sampleCancellationReason()
                        )
                ).whenComplete((result, error) -> {
                    if (error != null) {
                        logger.error(
                                "Failed to cancel order {}",
                                orderId,
                                error
                        );
                    }
                })
        );
    }

    private String sampleCancellationReason() {
        return CANCELLATION_REASONS[
                OlistSampling.random().nextInt(CANCELLATION_REASONS.length)
                ];
    }
}