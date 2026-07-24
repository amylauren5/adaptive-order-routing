package ict.um.orders.core_api.enums;

import java.math.BigInteger;
import java.util.Objects;

public enum OrderStatus {
    CREATED(BigInteger.ZERO),
    APPROVED(BigInteger.ONE),
    DISPATCHED(BigInteger.TWO),
    COMPLETED(BigInteger.valueOf(3)),
    CANCELLED(BigInteger.valueOf(4));

    private final BigInteger value;

    // Constructor to set the value for each state
    OrderStatus(BigInteger value) {
        this.value = value;
    }

    // Getter for the value
    public BigInteger getValue() {
        return value;
    }

    // Method to retrieve state from integer value
    public static OrderStatus fromValue(BigInteger value) {
        for (OrderStatus status : values()) {
            if (Objects.equals(status.getValue(), value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown state value: " + value);
    }
}

