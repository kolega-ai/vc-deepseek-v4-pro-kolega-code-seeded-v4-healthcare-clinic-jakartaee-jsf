package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.DocumentStatus;

import java.util.Set;

/**
 * Patient document lifecycle:
 * SUBMITTED -> {VERIFIED, REJECTED, ARCHIVED};
 * REJECTED  -> {VERIFIED, ARCHIVED};
 * VERIFIED  -> ARCHIVED.
 */
public final class DocumentWorkflow {

    public static final Set<String> ALLOWED = Set.of(
            "null->SUBMITTED",       // upload
            "SUBMITTED->VERIFIED",   // front desk review
            "SUBMITTED->REJECTED",
            "SUBMITTED->ARCHIVED",
            "REJECTED->VERIFIED",    // re-review after rejection
            "REJECTED->ARCHIVED",
            "VERIFIED->ARCHIVED"
    );

    private DocumentWorkflow() { }

    public static boolean allows(DocumentStatus from, DocumentStatus to) {
        return ALLOWED.contains(edge(from, to));
    }

    public static void require(DocumentStatus from, DocumentStatus to, String action) {
        if (!allows(from, to)) {
            throw new ValidationException("Action '" + action + "' cannot move a document from " + from + " to " + to + ".");
        }
    }

    private static String edge(DocumentStatus from, DocumentStatus to) {
        return (from == null ? "null" : from.name()) + "->" + to.name();
    }
}
