package com.openforge.aimate.billing;

public class InsufficientPointsException extends RuntimeException {

    public InsufficientPointsException(String message) {
        super(message);
    }
}
