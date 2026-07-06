package ict.um.pharmaceutical.coreapi.enums;

import java.math.BigInteger;
import java.util.Objects;

public enum State {
    MANUFACTURED(BigInteger.ZERO),
    SOLD_TO_DISTRIBUTOR(BigInteger.ONE),
    SHIPPED_TO_DISTRIBUTOR(BigInteger.TWO),
    DELIVERED_TO_DISTRIBUTOR(BigInteger.valueOf(3)),
    SOLD_TO_CONSUMER(BigInteger.valueOf(4)),
    SHIPPED_TO_CONSUMER(BigInteger.valueOf(5)),
    DELIVERED_TO_CONSUMER(BigInteger.valueOf(6)),
    CANCELLED_BY_CONSUMER(BigInteger.valueOf(7));

    private final BigInteger value;

    // Constructor to set the value for each state
    State(BigInteger value) {
        this.value = value;
    }

    // Getter for the value
    public BigInteger getValue() {
        return value;
    }

    // Method to retrieve state from integer value
    public static State fromValue(BigInteger value) {
        for (State state : values()) {
            if (Objects.equals(state.getValue(), value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown state value: " + value);
    }
}

