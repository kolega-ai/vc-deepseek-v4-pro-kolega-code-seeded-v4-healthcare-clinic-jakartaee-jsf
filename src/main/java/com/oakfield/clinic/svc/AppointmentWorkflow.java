package com.oakfield.clinic.svc;

import com.oakfield.clinic.model.AppointmentStatus;

import java.util.Set;

/**
 * Appointment state machine. The ALLOWED set is the complete, explicit edge
 * list - transitions are enumerated, not merely prohibited. Every status named
 * in the product spec is reachable only through these edges, and the service
 * layer rejects anything else before mutating state.
 */
public final class AppointmentWorkflow {

    /** Explicit allowed transitions (from -> to). "null" = creation. */
    public static final Set<String> ALLOWED = Set.of(
            "null->REQUESTED",        // patient or external booking (awaiting confirmation)
            "null->SCHEDULED",        // staff-managed scheduling
            "REQUESTED->SCHEDULED",   // front desk confirms the request
            "REQUESTED->CANCELLED",   // patient or staff cancels before confirmation
            "SCHEDULED->CHECKED_IN",  // front desk checks the patient in
            "SCHEDULED->CANCELLED",   // patient (inside cutoff) or staff cancels
            "SCHEDULED->NO_SHOW",     // staff marks no-show after the start time
            "SCHEDULED->COMPLETED",   // visit finished (incl. telephone visits)
            "CHECKED_IN->COMPLETED",  // visit finished after check-in
            "SCHEDULED->SCHEDULED"    // reschedule: time change only, stays scheduled
    );

    private AppointmentWorkflow() { }

    public static boolean allows(AppointmentStatus from, AppointmentStatus to) {
        return ALLOWED.contains(edge(from, to));
    }

    public static void require(AppointmentStatus from, AppointmentStatus to, String action) {
        if (!allows(from, to)) {
            throw new ValidationException(
                    "Action '" + action + "' cannot move an appointment from " + from + " to " + to + ".");
        }
    }

    private static String edge(AppointmentStatus from, AppointmentStatus to) {
        return (from == null ? "null" : from.name()) + "->" + to.name();
    }
}
