package ict.um.orders.exceptions;

public class OutOfOrderEventException extends RuntimeException {

    public OutOfOrderEventException(
            String orderId,
            int expected,
            int received
    ) {
        super(
                "Out-of-order event for order "
                        + orderId
                        + ": expected sequence "
                        + expected
                        + " but received "
                        + received
        );
    }
}