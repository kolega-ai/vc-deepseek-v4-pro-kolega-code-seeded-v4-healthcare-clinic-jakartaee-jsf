package com.oakfield.clinic.svc;

/** Domain validation failure; carries a user-facing message. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
