package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;

@Stateless
public class VisitService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientAccessService patientAccess;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    @EJB
    private IntegrationService integrations;

    public record VisitForm(String diagnosisText, String careInstructions, String followUpPlan, String clinicianNotes) { }

    /** Start a draft visit summary for a completed appointment (clinician only). */
    public VisitSummary createDraft(DecisionContext ctx, long appointmentId, VisitForm form) {
        ctx.require("visit.write");
        Appointment appointment = em.find(Appointment.class, appointmentId);
        if (appointment == null) throw new NotFoundException("Appointment not found.");
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ValidationException("A visit summary can only be written after the visit is completed.");
        }
        patientAccess.requirePatientAccess(ctx, appointment.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        VisitWorkflow.require(null, VisitStatus.DRAFT, "create-summary");
        Long existing = em.createQuery(
                        "select count(v) from VisitSummary v where v.appointment.id = :aid", Long.class)
                .setParameter("aid", appointmentId).getSingleResult();
        if (existing != null && existing > 0) {
            throw new ValidationException("A visit summary already exists for this appointment.");
        }
        VisitSummary v = new VisitSummary();
        v.setAppointment(appointment);
        v.setPatient(appointment.getPatient());
        v.setProvider(appointment.getProvider());
        v.setStatus(VisitStatus.DRAFT);
        applyForm(v, form);
        em.persist(v);
        audit.record(ctx, AuditService.VISIT_CREATED, "VISIT_SUMMARY", String.valueOf(v.getId()),
                null, Json.obj("appointment", String.valueOf(appointmentId), "status", "DRAFT"));
        return v;
    }

    /** Edit while DRAFT. Front desk has no visit.write scope and is blocked server-side. */
    public VisitSummary updateDraft(DecisionContext ctx, long summaryId, VisitForm form) {
        ctx.require("visit.write");
        VisitSummary v = find(summaryId);
        patientAccess.requirePatientAccess(ctx, v.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        VisitWorkflow.require(v.getStatus(), VisitStatus.DRAFT, "edit-summary");
        applyForm(v, form);
        v.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.VISIT_EDITED, "VISIT_SUMMARY", String.valueOf(summaryId),
                Json.obj("status", "DRAFT"), Json.obj("edited", "true"));
        return v;
    }

    /** Release: becomes patient-visible (terminal). */
    public VisitSummary release(DecisionContext ctx, long summaryId) {
        ctx.require("visit.release");
        VisitSummary v = find(summaryId);
        patientAccess.requirePatientAccess(ctx, v.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        VisitWorkflow.require(v.getStatus(), VisitStatus.RELEASED, "release-summary");
        if (v.getDiagnosisText() == null || v.getDiagnosisText().isBlank()) {
            throw new ValidationException("Add an assessment before releasing the summary.");
        }
        v.setStatus(VisitStatus.RELEASED);
        v.setReleasedAt(LocalDateTime.now());
        v.setReleasedBy(ctx.getUserId());
        v.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.VISIT_RELEASED, "VISIT_SUMMARY", String.valueOf(summaryId),
                Json.obj("status", "DRAFT"), Json.obj("status", "RELEASED"));
        if (v.getPatient().getUser() != null) {
            notifications.notify(v.getPatient().getUser().getId(), NotificationType.SUMMARY,
                    "New visit summary available",
                    "Your visit summary from " + v.getAppointment().getStartTime().toLocalDate() + " has been released.",
                    "/app/patient/summaries.xhtml");
        }
        integrations.dispatch("SUMMARY_RELEASED",
                Json.obj("summaryId", String.valueOf(summaryId), "patientId", String.valueOf(v.getPatient().getId())));
        return v;
    }

    /**
     * Discard a summary the clinician started by mistake (wrong appointment). Only
     * drafts are offered in the UI; the record is removed together with its audit
     * pointer so the chart does not show an empty entry.
     */
    public void discardSumary(DecisionContext ctx, long summaryId) {
        ctx.require("visit.write");
        VisitSummary v = find(summaryId);
        patientAccess.requirePatientAccess(ctx, v.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        em.remove(v);
        audit.record(ctx, AuditService.VISIT_EDITED, "VISIT_SUMMARY", String.valueOf(summaryId),
                Json.obj("status", String.valueOf(v.getStatus())), Json.obj("discarded", "true"));
    }

    /** Patient view: only their own RELEASED summaries (drafts are invisible here). */
    public VisitSummary getForPatient(DecisionContext ctx, long summaryId) {
        VisitSummary v = find(summaryId);
        patientAccess.requirePatientAccess(ctx, v.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        if (v.getStatus() != VisitStatus.RELEASED) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Visit summaries are visible after release.");
        }
        return v;
    }

    public List<VisitSummary> releasedForPatient(DecisionContext ctx, int limit) {
        Patient p = patientAccess.patientForUser(ctx);
        return em.createQuery(
                        "select v from VisitSummary v where v.patient.id = :pid and v.status = :rel order by v.releasedAt desc",
                        VisitSummary.class)
                .setParameter("pid", p.getId())
                .setParameter("rel", VisitStatus.RELEASED)
                .setMaxResults(limit == 0 ? Integer.MAX_VALUE : limit)
                .getResultList();
    }

    /** Clinician chart: all statuses for an assigned patient. */
    public List<VisitSummary> forChart(DecisionContext ctx, long patientId) {
        patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.CLINICIAN);
        return em.createQuery(
                        "select v from VisitSummary v where v.patient.id = :pid order by v.createdAt desc", VisitSummary.class)
                .setParameter("pid", patientId)
                .getResultList();
    }

    /** Drafts for the clinician's work queue. */
    public List<VisitSummary> draftsByProvider(DecisionContext ctx, long providerId) {
        ctx.require("visit.write");
        return em.createQuery(
                        "select v from VisitSummary v where v.provider.id = :pid and v.status = :draft order by v.updatedAt desc",
                        VisitSummary.class)
                .setParameter("pid", providerId)
                .setParameter("draft", VisitStatus.DRAFT)
                .getResultList();
    }

    public long countDraftsByProvider(DecisionContext ctx, long providerId) {
        ctx.require("visit.write");
        return em.createQuery(
                        "select count(v) from VisitSummary v where v.provider.id = :pid and v.status = :draft", Long.class)
                .setParameter("pid", providerId)
                .setParameter("draft", VisitStatus.DRAFT)
                .getSingleResult();
    }

    /** Clinician (or admin) access to a single summary in any status. */
    public VisitSummary getForClinician(DecisionContext ctx, long summaryId) {
        VisitSummary v = find(summaryId);
        patientAccess.requirePatientAccess(ctx, v.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        return v;
    }

    private VisitSummary find(long id) {
        VisitSummary v = em.find(VisitSummary.class, id);
        if (v == null) throw new NotFoundException("Visit summary not found.");
        return v;
    }

    private void applyForm(VisitSummary v, VisitForm form) {
        if (form.diagnosisText() == null || form.diagnosisText().isBlank() || form.diagnosisText().length() > 4000) {
            throw new ValidationException("Assessment/plan is required (max 4000 characters).");
        }
        if (form.careInstructions() != null && form.careInstructions().length() > 4000) {
            throw new ValidationException("Care instructions are too long (max 4000 characters).");
        }
        if (form.followUpPlan() != null && form.followUpPlan().length() > 2000) {
            throw new ValidationException("Follow-up plan is too long (max 2000 characters).");
        }
        if (form.clinicianNotes() != null && form.clinicianNotes().length() > 4000) {
            throw new ValidationException("Clinician notes are too long (max 4000 characters).");
        }
        v.setDiagnosisText(form.diagnosisText().strip());
        v.setCareInstructions(blankToNull(form.careInstructions()));
        v.setFollowUpPlan(blankToNull(form.followUpPlan()));
        v.setClinicianNotes(blankToNull(form.clinicianNotes()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
