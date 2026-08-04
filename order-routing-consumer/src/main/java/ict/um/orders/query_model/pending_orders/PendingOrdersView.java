package ict.um.orders.query_model.pending_orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "pending_orders_view",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_pending_order_sequence",
                columnNames = {"order_id", "sequence_number"}
        )
)
public class PendingOrdersView {

    @Id
    @Column(name = "routing_decision_id", nullable = false)
    private String routingDecisionId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "queue_name", nullable = false)
    private String queueName;

    @Column(
            name = "message_payload",
            columnDefinition = "TEXT",
            nullable = false
    )
    private String messagePayload;

    @Column(name = "received_at", nullable = false)
    private long receivedAt;

    protected PendingOrdersView() {
    }

    public PendingOrdersView(
            String routingDecisionId,
            String orderId,
            int sequenceNumber,
            String queueName,
            String messagePayload,
            long receivedAt
    ) {
        this.routingDecisionId = routingDecisionId;
        this.orderId = orderId;
        this.sequenceNumber = sequenceNumber;
        this.queueName = queueName;
        this.messagePayload = messagePayload;
        this.receivedAt = receivedAt;
    }

    public String getRoutingDecisionId() {
        return routingDecisionId;
    }

    public String getOrderId() {
        return orderId;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getMessagePayload() {
        return messagePayload;
    }

    public long getReceivedAt() {
        return receivedAt;
    }
}