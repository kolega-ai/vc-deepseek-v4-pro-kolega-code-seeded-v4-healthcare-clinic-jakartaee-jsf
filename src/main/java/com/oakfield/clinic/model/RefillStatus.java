package com.oakfield.clinic.model;

/**
 * Prescription refill request lifecycle:
 * REQUESTED -> {NEEDS_INFO, APPROVED, DENIED, CANCELLED}, NEEDS_INFO -> REQUESTED (resubmit).
 * Enforced by {@code RefillWorkflow.ALLOWED}.
 */
public enum RefillStatus { NONE, REQUESTED, NEEDS_INFO, APPROVED, DENIED, CANCELLED }
