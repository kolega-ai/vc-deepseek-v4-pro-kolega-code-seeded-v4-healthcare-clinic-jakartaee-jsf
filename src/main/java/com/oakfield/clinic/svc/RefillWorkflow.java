package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.RefillStatus;

import java.util.Set;

/**
 * Prescription refill request lifecycle:
 * REQUESTED -> {NEEDS_INFO, APPROVED, DENIED, CANCELLED};
 * NEEDS_INFO -> REQUESTED (patient resubmits with more information).
 */
public final class RefillWorkflow {

    public static final Set<String> ALLOWED = Set.of(
            "null->REQUESTED",       // patient requests a refill (from NONE)
            "REQUESTED->NEEDS_INFO", // clinician asks for more information
            "REQUESTED->APPROVED",   // clinician approves
            "REQUESTED->DENIED",     // clinician denies
            "REQUESTED->CANCELLED",  // patient withdraws the request
            "NEEDS_INFO->REQUESTED", // patient resubmits with an answer
            "NEEDS_INFO->APPROVED",
            "NEEDS_INFO->DENIED",
            "NEEDS_INFO->CANCELLED"
    );

    private RefillWorkflow() { }

    public static boolean allows(RefillStatus from, RefillStatus to) {
        return ALLOWED.contains(edge(from, to));
    }

    public static void require(RefillStatus from, RefillStatus to, String action) {
        if (!allows(from, to)) {
            throw new ValidationException("Action '" + action + "' cannot move a refill request from " + from + " to " + to + ".");
        }
    }

    private static String edge(RefillStatus from, RefillStatus to) {
        return (from == null ? "null" : from.name()) + "->" + to.name();
    }
}
