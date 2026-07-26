package ict.um.orders.services;

import ict.um.orders.config.QueueNames;
import ict.um.orders.routing.OrderRoutingContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "routing.strategy",
        havingValue = "rule-based",
        matchIfMissing = true
)
public class RuleBasedRoutingService implements RoutingService {

    @Override
    public String route(OrderRoutingContext context) {
        return switch (context.status()) {
            case CREATED -> QueueNames.LOW;
            case APPROVED, DISPATCHED -> QueueNames.MEDIUM;
            case COMPLETED, CANCELLED -> QueueNames.HIGH;
        };
    }
}