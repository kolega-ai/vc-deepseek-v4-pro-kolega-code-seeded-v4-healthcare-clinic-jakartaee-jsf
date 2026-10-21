package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.VisitStatus;

import java.util.Set;

/** Visit summary lifecycle: created as DRAFT, editable while draft, patient-visible only after RELEASED. */
public final class VisitWorkflow {

    public static final Set<String> ALLOWED = Set.of(
            "null->DRAFT",        // clinician starts the summary
            "DRAFT->DRAFT",       // clinician edits while drafting
            "DRAFT->RELEASED"     // release makes it patient-visible (terminal)
    );

    private VisitWorkflow() { }

    public static boolean allows(VisitStatus from, VisitStatus to) {
        return ALLOWED.contains(edge(from, to));
    }

    public static void require(VisitStatus from, VisitStatus to, String action) {
        if (!allows(from, to)) {
            throw new ValidationException("Action '" + action + "' cannot move a visit summary from " + from + " to " + to + ".");
        }
    }

    private static String edge(VisitStatus from, VisitStatus to) {
        return (from == null ? "null" : from.name()) + "->" + to.name();
    }
}
