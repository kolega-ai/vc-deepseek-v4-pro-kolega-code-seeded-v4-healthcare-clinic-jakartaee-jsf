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
public class PrescriptionService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientAccessService patientAccess;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    @EJB
    private MessageService messages;

    @EJB
    private IntegrationService integrations;

    @EJB
    private CoreMockClient coreMock;

    public record RxForm(String medicationName, String dosage, String frequency, String instructions,
                         int refillsRemaining, Long appointmentId) { }

    /** Clinician creates a prescription on a patient's chart. */
    public Prescription create(DecisionContext ctx, long patientId, RxForm form) {
        ctx.require("rx.write");
        Patient patient = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.CLINICIAN);
        Provider provider = em.createQuery(
                        "select p from Provider p where p.user.id = :uid", Provider.class)
                .setParameter("uid", ctx.getUserId())
                .getResultStream().findFirst()
                .orElseThrow(() -> new ValidationException("No provider profile is linked to this account."));
        validate(form);
        Prescription rx = new Prescription();
        rx.setPatient(patient);
        rx.setProvider(provider);
        rx.setMedicationName(form.medicationName().strip());
        rx.setDosage(form.dosage().strip());
        rx.setFrequency(form.frequency().strip());
        rx.setInstructions(blankToNull(form.instructions()));
        rx.setStatus(PrescriptionStatus.ACTIVE);
        rx.setRefillStatus(RefillStatus.NONE);
        rx.setRefillsRemaining(Math.max(0, Math.min(form.refillsRemaining(), 12)));
        if (form.appointmentId() != null) {
            rx.setAppointment(em.find(Appointment.class, form.appointmentId()));
        }
        em.persist(rx);
        audit.record(ctx, AuditService.RX_CREATED, "PRESCRIPTION", String.valueOf(rx.getId()),
                null, Json.obj("medication", rx.getMedicationName(), "patient", String.valueOf(patientId)));
        if (patient.getUser() != null) {
            notifications.notify(patient.getUser().getId(), NotificationType.PRESCRIPTION,
                    "New prescription",
                    "A new prescription for " + rx.getMedicationName() + " was added to your record.",
                    "/app/patient/prescriptions.xhtml");
        }
        return rx;
    }

    public Prescription discontinue(DecisionContext ctx, long prescriptionId) {
        ctx.require("rx.write");
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        if (rx.getStatus() != PrescriptionStatus.ACTIVE) {
            throw new ValidationException("Only active prescriptions can be discontinued.");
        }
        rx.setStatus(PrescriptionStatus.DISCONTINUED);
        if (rx.getRefillStatus() == RefillStatus.REQUESTED || rx.getRefillStatus() == RefillStatus.NEEDS_INFO) {
            rx.setRefillStatus(RefillStatus.CANCELLED);
        }
        audit.record(ctx, AuditService.RX_DISCONTINUED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("status", "ACTIVE"), Json.obj("status", "DISCONTINUED"));
        return rx;
    }

    // ------------------------------------------------------------ refill workflow

    /** Patient requests a refill (or resubmits from NEEDS_INFO). */
    public Prescription requestRefill(DecisionContext ctx, long prescriptionId, String note) {
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        ctx.require("refill.request");
        if (rx.getStatus() != PrescriptionStatus.ACTIVE) {
            throw new ValidationException("Only active prescriptions can be refilled.");
        }
        RefillStatus from = rx.getRefillStatus() == RefillStatus.NONE ? null : rx.getRefillStatus();
        RefillWorkflow.require(from, RefillStatus.REQUESTED, from == null ? "request-refill" : "resubmit-refill");
        if (note == null || note.isBlank() || note.length() > 1000) {
            throw new ValidationException("Tell us why you need the refill (max 1000 characters).");
        }
        rx.setRefillStatus(RefillStatus.REQUESTED);
        rx.setRefillRequestedAt(LocalDateTime.now());
        rx.setRefillRequestNote(note.strip());
        rx.setRefillResponseNote(null);
        rx.setRefillDecidedBy(null);
        rx.setRefillDecidedAt(null);
        audit.record(ctx, AuditService.REFILL_REQUESTED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", from == null ? "NONE" : from.name()), Json.obj("refill", "REQUESTED"));
        if (rx.getProvider().getUser() != null) {
            notifications.notify(rx.getProvider().getUser().getId(), NotificationType.PRESCRIPTION,
                    "Refill requested",
                    rx.getPatient().fullName() + " requested a refill of " + rx.getMedicationName() + ".",
                    "/app/staff/clinician/refills.xhtml");
        }
        return rx;
    }

    /** Staff files a refill request on behalf of a patient who called the clinic. */
    public Prescription requestRefillOnBehalf(DecisionContext ctx, long prescriptionId, String note) {
        ctx.require("refill.decide");
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        if (rx.getStatus() != PrescriptionStatus.ACTIVE) {
            throw new ValidationException("Only active prescriptions can be refilled.");
        }
        RefillStatus from = rx.getRefillStatus() == RefillStatus.NONE ? null : rx.getRefillStatus();
        RefillWorkflow.require(from, RefillStatus.REQUESTED, "request-refill-on-behalf");
        if (note == null || note.isBlank() || note.length() > 1000) {
            throw new ValidationException("Add a note about who called and what they asked for.");
        }
        rx.setRefillStatus(RefillStatus.REQUESTED);
        rx.setRefillRequestedAt(LocalDateTime.now());
        rx.setRefillRequestNote(note.strip());
        rx.setRefillResponseNote(null);
        rx.setRefillDecidedBy(null);
        rx.setRefillDecidedAt(null);
        audit.record(ctx, AuditService.REFILL_REQUESTED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", from == null ? "NONE" : from.name(), "via", "staff"), Json.obj("refill", "REQUESTED"));
        return rx;
    }

    /** Refill queue for clinicians (REQUESTED and NEEDS_INFO). */
    public List<Prescription> refillQueue(DecisionContext ctx) {
        ctx.require("refill.decide");
        return em.createQuery(
                        "select r from Prescription r where r.refillStatus in :s and r.status = :active order by r.refillRequestedAt asc",
                        Prescription.class)
                .setParameter("s", List.of(RefillStatus.REQUESTED, RefillStatus.NEEDS_INFO))
                .setParameter("active", PrescriptionStatus.ACTIVE)
                .getResultList();
    }

    public Prescription approveRefill(DecisionContext ctx, long prescriptionId, String note) {
        ctx.require("refill.decide");
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        RefillWorkflow.require(rx.getRefillStatus(), RefillStatus.APPROVED, "approve-refill");
        if (rx.getRefillsRemaining() <= 0) {
            throw new ValidationException("No refills remain on this prescription.");
        }
        rx.setRefillStatus(RefillStatus.APPROVED);
        rx.setRefillResponseNote(blankToNull(note));
        rx.setRefillDecidedBy(ctx.getUserId());
        rx.setRefillDecidedAt(LocalDateTime.now());
        rx.setRefillsRemaining(rx.getRefillsRemaining() - 1);
        // Approved refills are transmitted to the (mock) pharmacy clearinghouse.
        rx.setRefillTransmitStatus(RefillTransmitStatus.PENDING);
        CoreMockClient.TransmitResult result = coreMock.transmitRefill(
                rx.getId(), rx.getPatient().getId(), rx.getPatient().fullName(),
                rx.getMedicationName(), rx.getDosage(), rx.getProvider().getNpi());
        if (result.ok()) {
            rx.setRefillTransmitStatus(RefillTransmitStatus.SENT);
            rx.setRefillTransmitRef(result.reference());
        } else {
            rx.setRefillTransmitStatus(RefillTransmitStatus.FAILED);
        }
        audit.record(ctx, AuditService.REFILL_APPROVED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", "REQUESTED"), Json.obj("refill", "APPROVED",
                        "transmit", String.valueOf(rx.getRefillTransmitStatus()), "reference", rx.getRefillTransmitRef()));
        if (rx.getPatient().getUser() != null) {
            notifications.notify(rx.getPatient().getUser().getId(), NotificationType.PRESCRIPTION,
                    "Refill approved",
                    "Your refill of " + rx.getMedicationName() + " was approved and sent to the pharmacy.",
                    "/app/patient/prescriptions.xhtml");
        }
        integrations.dispatch("REFILL_APPROVED",
                Json.obj("prescriptionId", String.valueOf(prescriptionId), "patientId", String.valueOf(rx.getPatient().getId()),
                        "medication", rx.getMedicationName(), "reference", rx.getRefillTransmitRef()));
        return rx;
    }

    public Prescription denyRefill(DecisionContext ctx, long prescriptionId, String note) {
        ctx.require("refill.decide");
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        RefillWorkflow.require(rx.getRefillStatus(), RefillStatus.DENIED, "deny-refill");
        if (note == null || note.isBlank() || note.length() > 1000) {
            throw new ValidationException("Explain to the patient why the refill was denied.");
        }
        rx.setRefillStatus(RefillStatus.DENIED);
        rx.setRefillResponseNote(note.strip());
        rx.setRefillDecidedBy(ctx.getUserId());
        rx.setRefillDecidedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.REFILL_DENIED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", "REQUESTED"), Json.obj("refill", "DENIED", "note", note.strip()));
        if (rx.getPatient().getUser() != null) {
            notifications.notify(rx.getPatient().getUser().getId(), NotificationType.PRESCRIPTION,
                    "Refill needs review",
                    "Your refill request for " + rx.getMedicationName() + " was not approved. See the message from your provider.",
                    "/app/patient/prescriptions.xhtml");
        }
        return rx;
    }

    public Prescription needsInfoRefill(DecisionContext ctx, long prescriptionId, String question) {
        ctx.require("refill.decide");
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.CLINICIAN);
        RefillWorkflow.require(rx.getRefillStatus(), RefillStatus.NEEDS_INFO, "needs-info");
        if (question == null || question.isBlank() || question.length() > 1000) {
            throw new ValidationException("Ask the patient what additional information you need.");
        }
        rx.setRefillStatus(RefillStatus.NEEDS_INFO);
        rx.setRefillResponseNote(question.strip());
        rx.setRefillDecidedBy(ctx.getUserId());
        rx.setRefillDecidedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.REFILL_NEEDS_INFO, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", "REQUESTED"), Json.obj("refill", "NEEDS_INFO"));
        if (rx.getPatient().getUser() != null) {
            notifications.notify(rx.getPatient().getUser().getId(), NotificationType.PRESCRIPTION,
                    "More information needed for your refill",
                    "Your provider needs more information before refilling " + rx.getMedicationName() + ".",
                    "/app/patient/prescriptions.xhtml");
        }
        return rx;
    }

    /** Patient withdraws their own pending refill request. */
    public Prescription cancelRefill(DecisionContext ctx, long prescriptionId) {
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        ctx.require("refill.request");
        RefillWorkflow.require(rx.getRefillStatus(), RefillStatus.CANCELLED, "cancel-refill");
        rx.setRefillStatus(RefillStatus.CANCELLED);
        audit.record(ctx, AuditService.REFILL_CANCELLED, "PRESCRIPTION", String.valueOf(prescriptionId),
                Json.obj("refill", "REQUESTED"), Json.obj("refill", "CANCELLED"));
        return rx;
    }

    /** Background retry for approved refills whose clearinghouse transmission failed. */
    public int retryFailedTransmissions() {
        List<Prescription> failed = em.createQuery(
                        "select r from Prescription r where r.refillStatus = :approved and r.refillTransmitStatus = :failed",
                        Prescription.class)
                .setParameter("approved", RefillStatus.APPROVED)
                .setParameter("failed", RefillTransmitStatus.FAILED)
                .getResultList();
        int retried = 0;
        for (Prescription rx : failed) {
            CoreMockClient.TransmitResult result = coreMock.transmitRefill(
                    rx.getId(), rx.getPatient().getId(), rx.getPatient().fullName(),
                    rx.getMedicationName(), rx.getDosage(), rx.getProvider().getNpi());
            if (result.ok()) {
                rx.setRefillTransmitStatus(RefillTransmitStatus.SENT);
                rx.setRefillTransmitRef(result.reference());
                retried++;
            }
        }
        return retried;
    }

    // ------------------------------------------------------------ queries

    public List<Prescription> forPatientHome(DecisionContext ctx) {
        Patient p = patientAccess.patientForUser(ctx);
        return em.createQuery(
                        "select r from Prescription r where r.patient.id = :pid and r.status = :active order by r.createdAt desc",
                        Prescription.class)
                .setParameter("pid", p.getId())
                .setParameter("active", PrescriptionStatus.ACTIVE)
                .getResultList();
    }

    public List<Prescription> forChart(DecisionContext ctx, long patientId) {
        patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.CLINICIAN);
        return em.createQuery(
                        "select r from Prescription r where r.patient.id = :pid order by r.createdAt desc", Prescription.class)
                .setParameter("pid", patientId)
                .getResultList();
    }

    public Prescription getForPatient(DecisionContext ctx, long prescriptionId) {
        Prescription rx = find(prescriptionId);
        patientAccess.requirePatientAccess(ctx, rx.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        return rx;
    }

    private Prescription find(long id) {
        Prescription rx = em.find(Prescription.class, id);
        if (rx == null) throw new NotFoundException("Prescription not found.");
        return rx;
    }

    private void validate(RxForm form) {
        if (form.medicationName() == null || form.medicationName().isBlank() || form.medicationName().length() > 200) {
            throw new ValidationException("Medication name is required (max 200 characters).");
        }
        if (form.dosage() == null || form.dosage().isBlank() || form.dosage().length() > 120) {
            throw new ValidationException("Dosage is required (max 120 characters).");
        }
        if (form.frequency() == null || form.frequency().isBlank() || form.frequency().length() > 120) {
            throw new ValidationException("Frequency is required (max 120 characters).");
        }
        if (form.instructions() != null && form.instructions().length() > 1000) {
            throw new ValidationException("Instructions are too long (max 1000 characters).");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
