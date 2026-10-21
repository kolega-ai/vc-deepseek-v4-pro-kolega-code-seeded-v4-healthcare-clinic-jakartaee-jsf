package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import com.oakfield.clinic.svc.*;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Role-aware dashboard: patient, front desk, clinician, and admin summaries. */
@Named("dashboard")
@ViewScoped
public class DashboardBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;
    @Inject
    private VisitService visits;
    @Inject
    private PrescriptionService prescriptions;
    @Inject
    private DocumentService documents;
    @Inject
    private MessageService messages;
    @Inject
    private NotificationService notifications;
    @Inject
    private PatientAccessService patientAccess;
    @Inject
    private ProviderService providers;
    @Inject
    private AuditService audit;
    @Inject
    private PatientService patients;
    @Inject
    private UserAdminService users;
    @Inject
    private SettingsService settings;

    // patient
    private List<Appointment> upcoming;
    private List<VisitSummary> recentSummaries;
    private List<Prescription> activeRx;
    private long pendingForms;
    private long unreadMessages;

    // staff
    private List<Appointment> todayAll;
    private List<Appointment> myToday;
    private long requestedCount;
    private long formsQueueCount;
    private long draftCount;
    private List<Prescription> refillQueue;

    // admin
    private long userCount;
    private long patientCount;
    private long providerCount;
    private long todayAppointmentCount;
    private List<AuditEvent> recentAudit;

    private List<Notification> recentNotifications;
    private Patient myPatient;
    private Provider myProvider;

    @PostConstruct
    public void load() {
        DecisionContext ctx = session.ctx();
        recentNotifications = notifications.forUser(ctx.getUserId(), 0, 5);
        if (ctx.isPatient()) {
            myPatient = patientAccess.patientForUser(ctx);
            upcoming = appointments.upcomingForPatient(ctx, 4);
            recentSummaries = visits.releasedForPatient(ctx, 3);
            activeRx = prescriptions.forPatientHome(ctx);
            pendingForms = documents.countByStatus(ctx, DocumentStatus.SUBMITTED);
            unreadMessages = messages.unreadThreadCount(ctx);
        } else if (ctx.isStaff()) {
            todayAll = appointments.forDay(ctx, LocalDate.now(), null);
            requestedCount = appointments.countByStatus(ctx, AppointmentStatus.REQUESTED);
            formsQueueCount = documents.countByStatus(ctx, DocumentStatus.SUBMITTED);
            unreadMessages = messages.unreadThreadCount(ctx);
            if ("CLINICIAN".equals(ctx.getRole())) {
                myProvider = providers.byUserId(ctx.getUserId());
                if (myProvider != null) {
                    myToday = appointments.todayForProvider(ctx, myProvider.getId());
                    draftCount = visits.countDraftsByProvider(ctx, myProvider.getId());
                }
                refillQueue = prescriptions.refillQueue(ctx);
            }
        }
        if (session.isAdmin()) {
            userCount = users.countAll(ctx);
            patientCount = patients.countActive();
            providerCount = providers.listActive().size();
            todayAppointmentCount = appointments.countOnDay(ctx, LocalDate.now());
            recentAudit = audit.search(new AuditService.AuditFilter(null, null, null, null, null), 0, 5);
        }
    }

    public String greeting() {
        LocalDate now = LocalDate.now();
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    public String clinicName() { return settings.clinicName(); }

    public List<Appointment> getUpcoming() { return upcoming; }
    public List<VisitSummary> getRecentSummaries() { return recentSummaries; }
    public List<Prescription> getActiveRx() { return activeRx; }
    public long getPendingForms() { return pendingForms; }
    public long getUnreadMessages() { return unreadMessages; }
    public List<Appointment> getTodayAll() { return todayAll; }
    public List<Appointment> getMyToday() { return myToday; }
    public long getRequestedCount() { return requestedCount; }
    public long getFormsQueueCount() { return formsQueueCount; }
    public long getDraftCount() { return draftCount; }
    public List<Prescription> getRefillQueue() { return refillQueue; }
    public long getUserCount() { return userCount; }
    public long getPatientCount() { return patientCount; }
    public long getProviderCount() { return providerCount; }
    public long getTodayAppointmentCount() { return todayAppointmentCount; }
    public List<AuditEvent> getRecentAudit() { return recentAudit; }
    public List<Notification> getRecentNotifications() { return recentNotifications; }
    public Patient getMyPatient() { return myPatient; }
    public Provider getMyProvider() { return myProvider; }
}
