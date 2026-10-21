package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.Patient;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Shared patient record access service.
 *
 * PRECISION BASELINE (non-vulnerability, keep): the ownership/scope check is
 * performed in this shared service one call frame above the controllers -
 * every record service (visits, prescriptions, documents, messages,
 * appointments) calls {@link #requirePatientAccess} instead of performing an
 * inline check. The check itself verifies both the decision-tier scope AND the
 * object-level relationship (owner, staff role, or clinician assignment).
 */
@Stateless
public class PatientAccessService {

    public enum AccessLevel {
        /** The patient themselves; requires the user account linked to the patient record. */
        OWN,
        /** Any staff role with the patient.read scope. */
        STAFF,
        /** Clinician clinical access: requires assignment to the patient or one of their appointments. */
        CLINICIAN
    }

    @PersistenceContext
    private EntityManager em;

    public Patient requirePatientAccess(DecisionContext ctx, long patientId, AccessLevel level) {
        Patient patient = em.find(Patient.class, patientId);
        if (patient == null) {
            throw new NotFoundException("Patient not found.");
        }
        switch (level) {
            case OWN -> {
                ctx.require("patient.read.own");
                if (ctx.getUserId() == null || patient.getUser() == null
                        || !patient.getUser().getId().equals(ctx.getUserId())) {
                    throw new AccessDeniedException("Patients may only access their own records.");
                }
            }
            case STAFF -> {
                ctx.require("patient.read");
            }
            case CLINICIAN -> {
                ctx.require("patient.read");
                ctx.require("visit.read.clinical");
                if (!isClinicianAllowed(ctx, patient)) {
                    throw new AccessDeniedException(
                            "Clinician is not assigned to this patient and has no appointment with them.");
                }
            }
        }
        return patient;
    }

    /** The patient record linked to the acting user (patient role flows). */
    public Patient patientForUser(DecisionContext ctx) {
        if (ctx.getUserId() == null) throw new AccessDeniedException("No user linked to this session.");
        Patient patient = em.createQuery(
                        "select p from Patient p where p.user.id = :uid", Patient.class)
                .setParameter("uid", ctx.getUserId())
                .getResultStream().findFirst()
                .orElseThrow(() -> new NotFoundException("No patient record is linked to this account."));
        return patient;
    }

    /**
     * A clinician may open a chart when the patient lists them as primary
     * provider OR when the clinician has at least one appointment with the
     * patient. Clinic admins have oversight access via scope only.
     */
    public boolean isClinicianAllowed(DecisionContext ctx, Patient patient) {
        if ("CLINIC_ADMIN".equals(ctx.getRole()) && ctx.hasScope("visit.read.clinical")) {
            return true; // oversight access, audited like any chart read
        }
        if (!"CLINICIAN".equals(ctx.getRole())) return false;
        if (patient.getPrimaryProvider() != null && patient.getPrimaryProvider().getUser() != null
                && patient.getPrimaryProvider().getUser().getId().equals(ctx.getUserId())) {
            return true;
        }
        Long count = em.createQuery(
                        "select count(a) from Appointment a where a.patient.id = :pid and a.provider.user.id = :uid",
                        Long.class)
                .setParameter("pid", patient.getId())
                .setParameter("uid", ctx.getUserId())
                .getSingleResult();
        return count != null && count > 0;
    }
}
