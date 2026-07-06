package ict.um.orders.user_interface;

import ict.um.orders.coreapi.commands.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RequestMapping("/orders")
@RestController
public class OrderController {

    private final CommandGateway commandGateway;

    @Autowired
    public OrderController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    // --- CREATE ORDER ---
    @PostMapping("/create/{customer}/{category}/{value}/{items}")
    public CompletableFuture<ResponseEntity<Object>> createOrder(
            @PathVariable("customer") String customerId,
            @PathVariable("category") String category,
            @PathVariable("value") double orderValue,
            @PathVariable("items") int itemCount) {

        String orderId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        CreateOrderCommand command = new CreateOrderCommand(
                orderId,
                customerId,
                category,
                orderValue,
                itemCount,
                now,
                1,      // priority
                0       // sequence number
        );

        return commandGateway.send(command)
                .thenApply(result -> new ResponseEntity<>(
                        Map.of("orderId", orderId, "status", "Order created"),
                        HttpStatus.CREATED));
    }

    // --- APPROVE ORDER ---
    @PostMapping("/{orderId}/approve")
    public CompletableFuture<ResponseEntity<Object>> approveOrder(
            @PathVariable("orderId") String orderId) {

        ApproveOrderCommand command = new ApproveOrderCommand(
                orderId,
                System.currentTimeMillis(),
                1
        );

        return commandGateway.send(command)
                .thenApply(result -> new ResponseEntity<>(
                        Map.of("orderId", orderId, "status", "Order approved"),
                        HttpStatus.OK));
    }

    // --- DISPATCH ORDER ---
    @PostMapping("/{orderId}/dispatch")
    public CompletableFuture<ResponseEntity<Object>> dispatchOrder(
            @PathVariable("orderId") String orderId) {

        DispatchOrderCommand command = new DispatchOrderCommand(
                orderId,
                System.currentTimeMillis(),
                2
        );

        return commandGateway.send(command)
                .thenApply(result -> new ResponseEntity<>(
                        Map.of("orderId", orderId, "status", "Order dispatched"),
                        HttpStatus.OK));
    }

    // --- COMPLETE ORDER ---
    @PostMapping("/{orderId}/complete")
    public CompletableFuture<ResponseEntity<Object>> completeOrder(
            @PathVariable("orderId") String orderId) {

        CompleteOrderCommand command = new CompleteOrderCommand(
                orderId,
                System.currentTimeMillis(),
                3
        );

        return commandGateway.send(command)
                .thenApply(result -> new ResponseEntity<>(
                        Map.of("orderId", orderId, "status", "Order completed"),
                        HttpStatus.OK));
    }

    // --- CANCEL ORDER ---
    @PostMapping("/{orderId}/cancel")
    public CompletableFuture<ResponseEntity<Object>> cancelOrder(
            @PathVariable("orderId") String orderId) {

        CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                System.currentTimeMillis(),
                1,
                "manual_cancellation"
        );

        return commandGateway.send(command)
                .thenApply(result -> new ResponseEntity<>(
                        Map.of("orderId", orderId, "status", "Order cancelled"),
                        HttpStatus.OK));
    }
}