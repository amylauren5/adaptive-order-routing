package ict.um.orders.routing;

import ict.um.orders.core_api.enums.OrderStatus;

public record OrderRoutingContext(
        String orderId,
        OrderStatus status,
        String category,
        double orderValue,
        int itemCount,
        long timestamp
) {
}