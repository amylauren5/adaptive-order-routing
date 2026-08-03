package ict.um.orders.query_model.pending_orders;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "pending_orders_view",
        uniqueConstraints = @UniqueConstraint (
                name = "unique_pending_order_sequence",
                columnNames = {"orderId", "sequenceNumber"}
        )
)
public class PendingOrdersView {

    @Id
    private String routingDecisionId;

    private String orderId;
    private int sequenceNumber;
    private String queueName;

    @jakarta.persistence.Lob
    private String messagePayload;

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
