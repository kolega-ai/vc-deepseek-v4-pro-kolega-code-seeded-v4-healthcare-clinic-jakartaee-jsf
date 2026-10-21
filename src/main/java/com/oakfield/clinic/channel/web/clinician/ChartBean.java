package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AuditEvent;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.model.MessageThread;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.Prescription;
import com.oakfield.clinic.model.VisitSummary;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.AuditService;
import com.oakfield.clinic.svc.CoreMockClient;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.Json;
import com.oakfield.clinic.svc.MessageService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.PrescriptionService;
import com.oakfield.clinic.svc.VisitService;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** The patient chart: header, eligibility, and tabbed clinical record. */
@Named("chart")
@ViewScoped
public class ChartBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private PatientAccessService patientAccess;
    @Inject
    private AppointmentService appointments;
    @Inject
    private VisitService visits;
    @Inject
    private PrescriptionService prescriptions;
    @Inject
    private MessageService messages;
    @Inject
    private DocumentService documents;
    @Inject
    private AuditService audit;
    @Inject
    private CoreMockClient coreMock;

    private long patientId;
    private Patient patient;
    private List<Appointment> appts = Collections.emptyList();
    private List<VisitSummary> summaries = Collections.emptyList();
    private List<Prescription> rxList = Collections.emptyList();
    private List<MessageThread> threads = Collections.emptyList();
    private List<Document> docs = Collections.emptyList();
    private List<AuditEvent> auditEvents = Collections.emptyList();
    private CoreMockClient.EligibilityResult eligibility;
    private String tab = "overview";

    public void load() {
        DecisionContext ctx = session.ctx();
        patientId = FacesUtil.paramLong("id", -1);
        patient = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.CLINICIAN);
        appts = appointments.search(ctx,
                new AppointmentService.AppointmentFilter(null, null, null, patientId, null), 0, 20).items();
        summaries = visits.forChart(ctx, patientId);
        rxList = prescriptions.forChart(ctx, patientId);
        threads = messages.threadsForPatient(ctx, patientId);
        docs = documents.search(ctx, new DocumentService.DocFilter(patientId, null, null, null), 0, 20).items();
        auditEvents = audit.forPatient(patientId, 0, 15);
        eligibility = coreMock.checkEligibility(patientId, patient.getDateOfBirth(),
                patient.getInsuranceProvider(), patient.getInsurancePolicyNumber());
        long records = appts.size() + summaries.size() + rxList.size()
                + threads.size() + docs.size() + auditEvents.size();
        // ONE chart-read audit event per page load, naming the record count.
        audit.record(ctx, AuditService.CHART_VIEW, "PATIENT", String.valueOf(patientId), null,
                Json.obj("records", String.valueOf(records), "role", ctx.getRole()));
    }

    public void selectTab(String t) {
        if (t != null && !t.isBlank()) {
            this.tab = t;
        }
    }

    public long getPatientId() { return patientId; }

    public Patient getPatient() { return patient; }

    public List<Appointment> getAppts() { return appts; }

    public List<VisitSummary> getSummaries() { return summaries; }

    public List<Prescription> getRxList() { return rxList; }

    public List<MessageThread> getThreads() { return threads; }

    public List<Document> getDocs() { return docs; }

    public List<AuditEvent> getAuditEvents() { return auditEvents; }

    public CoreMockClient.EligibilityResult getEligibility() { return eligibility; }

    public String getTab() { return tab; }
}
