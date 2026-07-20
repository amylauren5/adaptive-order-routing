package ict.um.orders;

import ict.um.orders.coreapi.commands.*;
import ict.um.orders.services.HashingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.axonframework.commandhandling.gateway.CommandGateway;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class WorkloadGenerator {

    private final CommandGateway commandGateway;
    private final HashingService hashingService;

    public WorkloadGenerator(CommandGateway commandGateway,
                             HashingService hashingService) {
        this.commandGateway = commandGateway;
        this.hashingService = hashingService;
    }

    @Scheduled(fixedRate = 5_000) // synthetic inter-arrival
    public void generateOrderLifecycle() {

        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        // deterministic item count
        int itemCount = 1 + OlistSampling.random().nextInt(5);

        long now = System.currentTimeMillis();

        // --- TEMP COMMAND FOR HASHING ---
        CreateOrderCommand temp = new CreateOrderCommand(
                orderId,
                customerId,
                OlistSampling.sampleCategory(),
                OlistSampling.sampleOrderValue(),
                itemCount,
                now,
                1,      // priority
                0,      // sequence number
                ""      // placeholder hash
        );

        String dataHash = hashingService.computeInitialDataHash(temp);

        // --- FINAL COMMAND WITH CORRECT HASH ---
        CreateOrderCommand createCmd = new CreateOrderCommand(
                orderId,
                customerId,
                temp.getCategory(),
                temp.getOrderValue(),
                itemCount,
                now,
                1,
                0,
                dataHash
        );

        // 1. Create order
        commandGateway.send(createCmd);

        // --- POSSIBLE CANCELLATION (deterministic) ---
        if (OlistSampling.random().nextDouble() < 0.10) {
            scheduleCancellation(orderId);
            return; // stop lifecycle if cancelled
        }

        // 2. Approval
        CompletableFuture.delayedExecutor(
                OlistSampling.sampleApprovalDelay(),
                TimeUnit.MILLISECONDS
        ).execute(() -> commandGateway.send(
                new ApproveOrderCommand(
                        orderId,
                        System.currentTimeMillis(),
                        1
                )
        ));

        // 3. Dispatch
        CompletableFuture.delayedExecutor(
                OlistSampling.sampleDispatchDelay(),
                TimeUnit.MILLISECONDS
        ).execute(() -> commandGateway.send(
                new DispatchOrderCommand(
                        orderId,
                        System.currentTimeMillis(),
                        2
                )
        ));

        // 4. Delivery
        CompletableFuture.delayedExecutor(
                OlistSampling.sampleDeliveryDelay(),
                TimeUnit.MILLISECONDS
        ).execute(() -> commandGateway.send(
                new CompleteOrderCommand(
                        orderId,
                        System.currentTimeMillis(),
                        3
                )
        ));
    }

    private void scheduleCancellation(String orderId) {

        long delay = OlistSampling.sampleApprovalDelay() / 2;

        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                .execute(() -> commandGateway.send(
                        new CancelOrderCommand(
                                orderId,
                                System.currentTimeMillis(),
                                1,
                                sampleCancellationReason()
                        )
                ));
    }

    private String sampleCancellationReason() {
        String[] reasons = {
                "customer_request",
                "payment_issue",
                "inventory_unavailable",
                "fraud_suspected"
        };

        // deterministic cancellation reason
        return reasons[OlistSampling.random().nextInt(reasons.length)];
    }
}