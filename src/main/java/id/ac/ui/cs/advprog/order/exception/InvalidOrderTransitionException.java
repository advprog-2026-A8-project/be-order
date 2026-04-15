package id.ac.ui.cs.advprog.order.exception;

public class InvalidOrderTransitionException extends IllegalArgumentException {
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}
