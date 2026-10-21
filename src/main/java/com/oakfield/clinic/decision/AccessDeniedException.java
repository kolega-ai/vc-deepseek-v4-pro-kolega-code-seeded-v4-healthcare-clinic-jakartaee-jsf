package com.oakfield.clinic.decision;

/** Thrown when the acting principal is not permitted to perform an action on a resource. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
