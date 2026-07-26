package ict.um.orders.services.routing;

import ict.um.orders.routing.OrderRoutingContext;

public interface RoutingService {
    String route(OrderRoutingContext context);
}