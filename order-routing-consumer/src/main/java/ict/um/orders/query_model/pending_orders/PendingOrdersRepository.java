package ict.um.orders.query_model.pending_orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PendingOrdersRepository
        extends JpaRepository<PendingOrdersView, String> {

    Optional<PendingOrdersView> findByOrderIdAndSequenceNumber(
            String orderId,
            int sequenceNumber
    );
}