package ict.um.orders;

import ict.um.orders.coreapi.commands.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.axonframework.commandhandling.gateway.CommandGateway;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class WorkloadGenerator {

    private final CommandGateway commandGateway;

    public WorkloadGenerator(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    @Scheduled(fixedRate = 5_000) // synthetic inter-arrival
    public void generateOrderLifecycle() {

        String orderId = UUID.randomUUID().toString();
        String customerId = UUID.randomUUID().toString();

        int itemCount = 1 + (int)(Math.random() * 5);

        long now = System.currentTimeMillis();

        // 1. Create order
        commandGateway.send(new CreateOrderCommand(
                orderId,
                customerId,
                OlistSampling.sampleCategory(),
                OlistSampling.sampleOrderValue(),
                itemCount,
                now,
                /* priority */ 1,
                /* sequenceNumber */ 0
        ));

        // --- POSSIBLE CANCELLATION ---
        // Olist cancellation rate ≈ 10%
        if (Math.random() < 0.10) {
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
        return reasons[(int) (Math.random() * reasons.length)];
    }
}
