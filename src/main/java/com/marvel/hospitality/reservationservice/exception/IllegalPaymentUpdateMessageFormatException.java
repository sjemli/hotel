package com.marvel.hospitality.reservationservice.exception;

public class IllegalPaymentUpdateMessageFormatException extends RuntimeException {
    public IllegalPaymentUpdateMessageFormatException(String message) {
        super(message);
    }
}