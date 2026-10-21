package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Stateless
public class AppointmentService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientAccessService patientAccess;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    @EJB
    private ScheduleService schedules;

    @EJB
    private IntegrationService integrations;

    @EJB
    private SettingsService settings;

    // ------------------------------------------------------------ queries

    public record AppointmentFilter(LocalDate from, LocalDate to, Long providerId, Long patientId,
                                    AppointmentStatus status) { }

    /** List/search. Patients always see their own appointments only; staff see all with filters. */
    public PageResult<Appointment> search(DecisionContext ctx, AppointmentFilter filter, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" where 1=1");
        Long patientId = filter.patientId();
        if (ctx.isPatient()) {
            ctx.require("appointment.book");
            Patient own = patientAccess.patientForUser(ctx);
            patientId = own.getId();
        } else {
            ctx.require("patient.read");
        }
        if (patientId != null) where.append(" and a.patient.id = :patient");
        if (filter.providerId() != null) where.append(" and a.provider.id = :provider");
        if (filter.status() != null) where.append(" and a.status = :status");
        if (filter.from() != null) where.append(" and a.startTime >= :from");
        if (filter.to() != null) where.append(" and a.startTime < :to");

        TypedQuery<Appointment> listQ = em.createQuery(
                "select a from Appointment a" + where + " order by a.startTime desc, a.id desc", Appointment.class);
        TypedQuery<Long> countQ = em.createQuery("select count(a) from Appointment a" + where, Long.class);
        if (patientId != null) { listQ.setParameter("patient", patientId); countQ.setParameter("patient", patientId); }
        if (filter.providerId() != null) { listQ.setParameter("provider", filter.providerId()); countQ.setParameter("provider", filter.providerId()); }
        if (filter.status() != null) { listQ.setParameter("status", filter.status()); countQ.setParameter("status", filter.status()); }
        if (filter.from() != null) { listQ.setParameter("from", filter.from().atStartOfDay()); countQ.setParameter("from", filter.from().atStartOfDay()); }
        if (filter.to() != null) { listQ.setParameter("to", filter.to().plusDays(1).atStartOfDay()); countQ.setParameter("to", filter.to().plusDays(1).atStartOfDay()); }
        List<Appointment> items = listQ.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList();
        return new PageResult<>(items, countQ.getSingleResult(), page, pageSize);
    }

    /**
     * Chart-scoped listing for the API: the caller has already resolved which chart
     * is in scope; this only applies the filter (staff need patient.read as usual).
     */
    public PageResult<Appointment> searchForChart(DecisionContext ctx, long chartPatientId,
                                                  AppointmentFilter filter, int page, int pageSize) {
        if (!ctx.isPatient()) ctx.require("patient.read");
        TypedQuery<Appointment> listQ = em.createQuery(
                "select a from Appointment a where a.patient.id = :patient and a.startTime >= :from and a.startTime < :to"
                        + " order by a.startTime desc, a.id desc", Appointment.class);
        TypedQuery<Long> countQ = em.createQuery(
                "select count(a) from Appointment a where a.patient.id = :patient and a.startTime >= :from and a.startTime < :to",
                Long.class);
        for (var q : List.of(listQ, countQ)) {
            q.setParameter("patient", chartPatientId);
            q.setParameter("from", filter.from().atStartOfDay());
            q.setParameter("to", filter.to().plusDays(1).atStartOfDay());
        }
        List<Appointment> items = listQ.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList();
        return new PageResult<>(items, countQ.getSingleResult(), page, pageSize);
    }

    /** Access-checked single fetch: patient sees own, staff sees any. */
    public Appointment get(DecisionContext ctx, long appointmentId) {
        Appointment a = em.find(Appointment.class, appointmentId);
        if (a == null) throw new NotFoundException("Appointment not found.");
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, a.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        } else {
            ctx.require("patient.read");
        }
        return a;
    }

    public List<Appointment> upcomingForPatient(DecisionContext ctx, int limit) {
        Patient p = patientAccess.patientForUser(ctx);
        return em.createQuery(
                        "select a from Appointment a where a.patient.id = :pid and a.startTime >= :now " +
                        "and a.status in :live order by a.startTime asc", Appointment.class)
                .setParameter("pid", p.getId())
                .setParameter("now", LocalDateTime.now())
                .setParameter("live", List.of(AppointmentStatus.REQUESTED, AppointmentStatus.SCHEDULED))
                .setMaxResults(limit)
                .getResultList();
    }

    /** Schedule board: all appointments for a provider on a day, or clinic-wide for a day. */
    public List<Appointment> forDay(DecisionContext ctx, LocalDate day, Long providerId) {
        ctx.require("schedule.read");
        StringBuilder jpql = new StringBuilder(
                "select a from Appointment a where a.startTime >= :from and a.startTime < :to");
        if (providerId != null) jpql.append(" and a.provider.id = :provider");
        jpql.append(" order by a.startTime asc");
        TypedQuery<Appointment> q = em.createQuery(jpql.toString(), Appointment.class)
                .setParameter("from", day.atStartOfDay())
                .setParameter("to", day.plusDays(1).atStartOfDay());
        if (providerId != null) q.setParameter("provider", providerId);
        return q.getResultList();
    }

    /** Front desk confirmation queue: unconfirmed patient requests. */
    public List<Appointment> requestedQueue(DecisionContext ctx) {
        ctx.require("appointment.manage");
        return em.createQuery(
                        "select a from Appointment a where a.status = :s and a.startTime >= :now order by a.startTime asc",
                        Appointment.class)
                .setParameter("s", AppointmentStatus.REQUESTED)
                .setParameter("now", LocalDateTime.now().minusHours(1))
                .getResultList();
    }

    /** Clinician's appointments for today (their provider identity). */
    public List<Appointment> todayForProvider(DecisionContext ctx, long providerId) {
        ctx.require("patient.read");
        LocalDate today = LocalDate.now();
        return em.createQuery(
                        "select a from Appointment a where a.provider.id = :pid and a.startTime >= :from and a.startTime < :to " +
                        "and a.status in :live order by a.startTime asc", Appointment.class)
                .setParameter("pid", providerId)
                .setParameter("from", today.atStartOfDay())
                .setParameter("to", today.plusDays(1).atStartOfDay())
                .setParameter("live", List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CHECKED_IN))
                .getResultList();
    }

    // ------------------------------------------------------------ booking

    /** Available slots for the booking screens and the JSON availability API. */
    public List<LocalDateTime> availableSlots(DecisionContext ctx, long providerId, LocalDate date, long typeId) {
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new ValidationException("Date must be today or later.");
        }
        AppointmentType type = em.find(AppointmentType.class, typeId);
        if (type == null || !type.isActive()) throw new NotFoundException("Appointment type not available.");
        if (ctx.isPatient()) {
            ctx.require("appointment.book");
            int maxDays = ctx.limitInt("appointment.book.max_future_days", 90);
            if (date.isAfter(LocalDate.now().plusDays(maxDays))) {
                throw new ValidationException("Appointments can be booked at most " + maxDays + " days ahead.");
            }
        } else {
            ctx.require("patient.read");
        }
        return schedules.availableSlots(providerId, date, type);
    }

    /** Patient or external integration booking: creates a REQUESTED appointment. */
    public Appointment bookRequested(DecisionContext ctx, long patientId, long providerId, long typeId,
                                     LocalDateTime start, String reason, String notes) {
        ctx.require("appointment.book");
        if (!ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        } else {
            patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.OWN);
        }
        return createAppointment(ctx, patientId, providerId, typeId, start, reason, notes, AppointmentStatus.REQUESTED);
    }

    /** Staff-managed scheduling: creates the appointment directly as SCHEDULED. */
    public Appointment bookScheduled(DecisionContext ctx, long patientId, long providerId, long typeId,
                                     LocalDateTime start, String reason, String notes) {
        ctx.require("appointment.book");
        patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        return createAppointment(ctx, patientId, providerId, typeId, start, reason, notes, AppointmentStatus.SCHEDULED);
    }

    /**
     * External scheduling integration (API key with scope appointment.request):
     * creates a REQUESTED appointment for the patient matched by email. The
     * patient id is resolved server-side from the email - never accepted from
     * the client - and the booking is audited with the API key as the actor.
     */
    public Appointment bookExternalRequest(DecisionContext ctx, long providerId, long typeId,
                                           LocalDateTime start, String patientEmail, String reason) {
        ctx.require("appointment.request");
        Patient patient = em.createQuery(
                        "select p from Patient p where lower(p.email) = lower(:email)", Patient.class)
                .setParameter("email", patientEmail.strip())
                .getResultStream().findFirst()
                .orElseThrow(() -> new ValidationException("No patient record matches that email."));
        return createAppointment(ctx, patient.getId(), providerId, typeId, start, reason, null,
                AppointmentStatus.REQUESTED);
    }

    private Appointment createAppointment(DecisionContext ctx, long patientId, long providerId, long typeId,
                                          LocalDateTime start, String reason, String notes, AppointmentStatus status) {
        AppointmentWorkflow.require(null, status, status == AppointmentStatus.REQUESTED ? "book-request" : "staff-book");
        Patient patient = em.find(Patient.class, patientId);
        Provider provider = em.find(Provider.class, providerId);
        AppointmentType type = em.find(AppointmentType.class, typeId);
        if (patient == null || !patient.isActive()) throw new NotFoundException("Patient not found or inactive.");
        if (provider == null || !provider.isActive()) throw new NotFoundException("Provider not found or inactive.");
        if (type == null || !type.isActive()) throw new NotFoundException("Appointment type not available.");
        if (start == null || !start.isAfter(LocalDateTime.now())) {
            throw new ValidationException("Appointment time must be in the future.");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ValidationException("Please provide a reason for the visit (max 1000 characters).");
        }
        LocalDate date = start.toLocalDate();
        if (ctx.isPatient()) {
            int maxDays = ctx.limitInt("appointment.book.max_future_days", 90);
            if (date.isAfter(LocalDate.now().plusDays(maxDays))) {
                throw new ValidationException("Appointments can be booked at most " + maxDays + " days ahead.");
            }
        }
        if (!schedules.availableSlots(providerId, date, type).contains(start)) {
            throw new ValidationException("That time slot is no longer available.");
        }
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setProvider(provider);
        a.setType(type);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(type.getDurationMinutes()));
        a.setStatus(status);
        a.setReason(reason.strip());
        a.setNotes(notes == null || notes.isBlank() ? null : notes.strip());
        a.setCreatedByUserId(ctx.getUserId());
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        em.persist(a);
        audit.record(ctx, AuditService.APPOINTMENT_BOOKED, "APPOINTMENT", String.valueOf(a.getId()),
                null, Json.obj("status", status.name(), "start", String.valueOf(start),
                        "provider", provider.fullName(), "patient", patient.fullName()));
        notifyPatient(patient, "Appointment " + (status == AppointmentStatus.REQUESTED ? "requested" : "scheduled"),
                (status == AppointmentStatus.REQUESTED ? "Your appointment request for " : "Your appointment for ")
                        + fmt(start) + " with " + provider.fullName() + " was received.",
                "/app/patient/appointments.xhtml");
        integrations.dispatch(status == AppointmentStatus.REQUESTED ? "APPOINTMENT_REQUESTED" : "APPOINTMENT_BOOKED",
                Json.obj("appointmentId", String.valueOf(a.getId()), "patientId", String.valueOf(patientId),
                        "providerId", String.valueOf(providerId), "start", String.valueOf(start),
                        "type", type.getCode(), "status", status.name()));
        return a;
    }

    // ------------------------------------------------------------ transitions

    public Appointment confirm(DecisionContext ctx, long appointmentId) {
        ctx.require("appointment.manage");
        Appointment a = find(appointmentId);
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.SCHEDULED, "confirm");
        a.setStatus(AppointmentStatus.SCHEDULED);
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_CONFIRMED, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", "REQUESTED"), Json.obj("status", "SCHEDULED"));
        notifyPatient(a.getPatient(), "Appointment confirmed",
                "Your appointment on " + fmt(a.getStartTime()) + " with " + a.getProvider().fullName() + " is confirmed.",
                "/app/patient/appointments.xhtml");
        integrations.dispatch("APPOINTMENT_CONFIRMED",
                Json.obj("appointmentId", String.valueOf(appointmentId), "start", String.valueOf(a.getStartTime())));
        return a;
    }

    public Appointment checkIn(DecisionContext ctx, long appointmentId) {
        ctx.require("appointment.checkin");
        Appointment a = find(appointmentId);
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.CHECKED_IN, "check-in");
        if (!a.getStartTime().toLocalDate().equals(LocalDate.now())) {
            throw new ValidationException("Patients can only be checked in on the day of their appointment.");
        }
        a.setStatus(AppointmentStatus.CHECKED_IN);
        a.setCheckedInAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_CHECKED_IN, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", "SCHEDULED"), Json.obj("status", "CHECKED_IN"));
        return a;
    }

    /**
     * Patient self check-in: on the day of the visit the portal issues a short
     * code the patient types at the waiting-room tablet to be marked checked in.
     */
    public String issueCheckInCode(DecisionContext ctx, long appointmentId) {
        Appointment a = find(appointmentId);
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, a.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        } else {
            ctx.require("appointment.manage");
        }
        if (!a.getStartTime().toLocalDate().equals(LocalDate.now())) {
            throw new ValidationException("A check-in code is only available on the day of the appointment.");
        }
        String code = String.valueOf(100000 + new java.util.Random().nextInt(900000));
        a.setCheckinCode(code);
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_CHECKED_IN, "APPOINTMENT", String.valueOf(appointmentId),
                null, Json.obj("checkInCode", "issued"));
        return code;
    }

    /** Redeem the short check-in code at the waiting-room tablet. */
    public Appointment confirmCheckInCode(DecisionContext ctx, long appointmentId, String code) {
        Appointment a = find(appointmentId);
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, a.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        } else {
            ctx.require("appointment.checkin");
        }
        if (code == null || a.getCheckinCode() == null || !code.strip().equals(a.getCheckinCode())) {
            throw new ValidationException("That check-in code is not valid.");
        }
        if (!a.getStartTime().toLocalDate().equals(LocalDate.now())) {
            throw new ValidationException("Patients can only be checked in on the day of their appointment.");
        }
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.CHECKED_IN, "self-check-in");
        a.setStatus(AppointmentStatus.CHECKED_IN);
        a.setCheckedInAt(LocalDateTime.now());
        a.setCheckinCode(null);
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_CHECKED_IN, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", "SCHEDULED"), Json.obj("status", "CHECKED_IN"));
        return a;
    }

    public Appointment complete(DecisionContext ctx, long appointmentId) {
        ctx.require("appointment.complete");
        Appointment a = find(appointmentId);
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.COMPLETED, "complete");
        a.setStatus(AppointmentStatus.COMPLETED);
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_COMPLETED, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", a.getStatus().name()), Json.obj("status", "COMPLETED"));
        return a;
    }

    /** Cancellation: patients (their own, within the cutoff) or staff (any). */
    public Appointment cancel(DecisionContext ctx, long appointmentId, String reason) {
        Appointment a = find(appointmentId);
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, a.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
            ctx.require("appointment.cancel.own");
            if (a.getStatus() == AppointmentStatus.SCHEDULED) {
                int cutoff = ctx.limitInt("appointment.cancel.cutoff_hours", 24);
                if (a.getStartTime().isBefore(LocalDateTime.now().plusHours(cutoff))) {
                    throw new ValidationException("Appointments can only be cancelled at least "
                            + cutoff + " hours in advance. Please call the clinic for help.");
                }
            }
        } else {
            ctx.require("appointment.manage");
        }
        AppointmentStatus from = a.getStatus();
        AppointmentWorkflow.require(from, AppointmentStatus.CANCELLED, "cancel");
        if ((reason == null || reason.isBlank()) && ctx.isPatient()) {
            throw new ValidationException("Please tell us why you are cancelling.");
        }
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelledReason(reason == null ? null : reason.strip());
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_CANCELLED, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", from.name()), Json.obj("status", "CANCELLED", "reason", a.getCancelledReason()));
        notifyPatient(a.getPatient(), "Appointment cancelled",
                "Your appointment on " + fmt(a.getStartTime()) + " was cancelled.", "/app/patient/appointments.xhtml");
        integrations.dispatch("APPOINTMENT_CANCELLED",
                Json.obj("appointmentId", String.valueOf(appointmentId), "start", String.valueOf(a.getStartTime())));
        return a;
    }

    public Appointment markNoShow(DecisionContext ctx, long appointmentId, String reason) {
        ctx.require("appointment.manage");
        Appointment a = find(appointmentId);
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.NO_SHOW, "mark-no-show");
        if (a.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ValidationException("A no-show can only be recorded after the appointment time has passed.");
        }
        a.setStatus(AppointmentStatus.NO_SHOW);
        a.setCancelledReason(reason == null ? "Patient did not attend." : reason.strip());
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_NO_SHOW, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("status", "SCHEDULED"), Json.obj("status", "NO_SHOW"));
        notifyPatient(a.getPatient(), "Missed appointment",
                "We missed you for your appointment on " + fmt(a.getStartTime()) + ". Please call us to reschedule.",
                "/app/patient/appointments.xhtml");
        return a;
    }

    /** Staff reschedule: keeps the appointment SCHEDULED with a new time. */
    public Appointment reschedule(DecisionContext ctx, long appointmentId, LocalDateTime newStart) {
        ctx.require("appointment.manage");
        Appointment a = find(appointmentId);
        AppointmentWorkflow.require(a.getStatus(), AppointmentStatus.SCHEDULED, "reschedule");
        if (newStart == null || !newStart.isAfter(LocalDateTime.now())) {
            throw new ValidationException("New time must be in the future.");
        }
        if (!schedules.availableSlots(a.getProvider().getId(), newStart.toLocalDate(), a.getType()).contains(newStart)) {
            throw new ValidationException("That time slot is not available.");
        }
        LocalDateTime before = a.getStartTime();
        a.setStartTime(newStart);
        a.setEndTime(newStart.plusMinutes(a.getType().getDurationMinutes()));
        a.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.APPOINTMENT_RESCHEDULED, "APPOINTMENT", String.valueOf(appointmentId),
                Json.obj("start", String.valueOf(before)), Json.obj("start", String.valueOf(newStart)));
        notifyPatient(a.getPatient(), "Appointment rescheduled",
                "Your appointment was moved to " + fmt(newStart) + " with " + a.getProvider().fullName() + ".",
                "/app/patient/appointments.xhtml");
        return a;
    }

    /** Patient reschedule = cancel own appointment and open a new request (two stated transitions). */
    public Appointment reschedulePatient(DecisionContext ctx, long appointmentId, long providerId, long typeId,
                                         LocalDateTime newStart, String reason) {
        Appointment old = cancel(ctx, appointmentId, "Rescheduled to " + fmt(newStart));
        return bookRequested(ctx, old.getPatient().getId(), providerId, typeId, newStart, reason, null);
    }

    // ------------------------------------------------------------ helpers

    /** Clinic-wide appointment counts for staff dashboards. */
    public long countByStatus(DecisionContext ctx, AppointmentStatus status) {
        ctx.require("patient.read");
        return em.createQuery("select count(a) from Appointment a where a.status = :s", Long.class)
                .setParameter("s", status)
                .getSingleResult();
    }

    /** Count of appointments on a given day (any status). */
    public long countOnDay(DecisionContext ctx, LocalDate day) {
        ctx.require("patient.read");
        return em.createQuery(
                        "select count(a) from Appointment a where a.startTime >= :from and a.startTime < :to", Long.class)
                .setParameter("from", day.atStartOfDay())
                .setParameter("to", day.plusDays(1).atStartOfDay())
                .getSingleResult();
    }

    private Appointment find(long id) {
        Appointment a = em.find(Appointment.class, id);
        if (a == null) throw new NotFoundException("Appointment not found.");
        return a;
    }

    private void notifyPatient(Patient patient, String title, String body, String link) {
        if (patient.getUser() != null) {
            notifications.notify(patient.getUser().getId(), NotificationType.APPOINTMENT, title, body, link);
        }
    }

    private static String fmt(LocalDateTime t) {
        return t.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d, yyyy 'at' h:mm a"));
    }
}
