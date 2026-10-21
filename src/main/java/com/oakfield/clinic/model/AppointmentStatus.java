package com.oakfield.clinic.model;

/**
 * Appointment lifecycle. Every transition must appear in
 * {@code AppointmentWorkflow.ALLOWED}; the service layer enforces that edge
 * list, so a status can never be reached by an undeclared path.
 */
public enum AppointmentStatus { REQUESTED, SCHEDULED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW }
