package ict.um.orders.services.routing;

import ict.um.orders.routing.OrderRoutingContext;
import ict.um.orders.routing.RoutingDecision;

public interface RoutingService {
    RoutingDecision route(OrderRoutingContext context);
}