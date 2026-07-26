package ict.um.orders.services;

import ict.um.orders.routing.OrderRoutingContext;

public interface RoutingService {
    String route(OrderRoutingContext context);
}