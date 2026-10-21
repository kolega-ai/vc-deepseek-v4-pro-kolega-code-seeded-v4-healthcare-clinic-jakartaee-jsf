package com.oakfield.clinic.decision;

/** Thrown when a session/API token is missing, malformed, expired, or revoked. */
public class NotAuthenticatedException extends RuntimeException {
    public NotAuthenticatedException(String message) { super(message); }
}
