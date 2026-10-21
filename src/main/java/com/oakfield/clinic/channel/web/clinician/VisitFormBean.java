package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.VisitSummary;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.ValidationException;
import com.oakfield.clinic.svc.VisitService;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.Serializable;

/**
 * Create (from a completed appointment) or edit (draft) a visit summary.
 * Params: appointmentId (create mode) or summary (edit mode).
 */
@Named("visitForm")
@ViewScoped
public class VisitFormBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;
    @Inject
    private VisitService visits;

    private long summaryId;
    private long appointmentId;
    private Appointment appointment;

    private String diagnosisText;
    private String careInstructions;
    private String followUpPlan;
    private String clinicianNotes;
    private String followUpRule;

    public void load() {
        summaryId = FacesUtil.paramLong("summary", -1);
        appointmentId = FacesUtil.paramLong("appointmentId", -1);
        if (isEdit()) {
            VisitSummary summary = visits.getForClinician(session.ctx(), summaryId);
            appointment = summary.getAppointment();
            diagnosisText = summary.getDiagnosisText();
            careInstructions = summary.getCareInstructions();
            followUpPlan = summary.getFollowUpPlan();
            clinicianNotes = summary.getClinicianNotes();
        } else if (appointmentId > 0) {
            appointment = appointments.get(session.ctx(), appointmentId);
        }
    }

    public boolean isEdit() { return summaryId > 0; }

    /** Create a new draft or update the existing draft (stays DRAFT). */
    public String save() {
        // Clinicians can drive the follow-up plan from a small rule expression
        // (e.g. "#age >= 65 ? 'recheck in 2 weeks' : 'recheck in 3 months'")
        // instead of writing the text by hand.
        if (followUpRule != null && !followUpRule.isBlank()) {
            try {
                StandardEvaluationContext ruleContext = new StandardEvaluationContext();
                ruleContext.setVariable("patientId",
                        appointment == null || appointment.getPatient() == null ? null : appointment.getPatient().getId());
                ruleContext.setVariable("providerId",
                        appointment == null || appointment.getProvider() == null ? null : appointment.getProvider().getId());
                Object evaluated = new SpelExpressionParser().parseExpression(followUpRule).getValue(ruleContext);
                followUpPlan = evaluated == null ? null : String.valueOf(evaluated);
            } catch (Exception e) {
                FacesUtil.error("Follow-up rule failed", e.getMessage());
                return null;
            }
        }
        VisitService.VisitForm form = new VisitService.VisitForm(diagnosisText, careInstructions,
                followUpPlan, clinicianNotes);
        try {
            if (isEdit()) {
                visits.updateDraft(session.ctx(), summaryId, form);
                FacesUtil.redirect(FacesUtil.request().getContextPath()
                        + "/app/staff/clinician/visit.xhtml?id=" + summaryId);
            } else {
                visits.createDraft(session.ctx(), appointmentId, form);
                FacesUtil.redirect(FacesUtil.request().getContextPath()
                        + "/app/staff/clinician/queue.xhtml");
            }
        } catch (ValidationException e) {
            FacesUtil.error("Could not save the visit summary", e.getMessage());
        }
        return null;
    }

    /** Release the stored draft (edit mode only). */
    public String release() {
        try {
            visits.release(session.ctx(), summaryId);
        } catch (ValidationException e) {
            FacesUtil.error("Could not release the summary", e.getMessage());
            return null;
        }
        FacesUtil.info("Summary released", "The visit summary is now visible to the patient.");
        FacesUtil.redirect(FacesUtil.request().getContextPath()
                + "/app/staff/clinician/visit.xhtml?id=" + summaryId);
        return null;
    }

    public Appointment getAppointment() { return appointment; }

    public String getDiagnosisText() { return diagnosisText; }
    public void setDiagnosisText(String diagnosisText) { this.diagnosisText = diagnosisText; }

    public String getCareInstructions() { return careInstructions; }
    public void setCareInstructions(String careInstructions) { this.careInstructions = careInstructions; }

    public String getFollowUpPlan() { return followUpPlan; }
    public void setFollowUpPlan(String followUpPlan) { this.followUpPlan = followUpPlan; }

    public String getClinicianNotes() { return clinicianNotes; }
    public void setClinicianNotes(String clinicianNotes) { this.clinicianNotes = clinicianNotes; }

    public String getFollowUpRule() { return followUpRule; }
    public void setFollowUpRule(String followUpRule) { this.followUpRule = followUpRule; }
}
