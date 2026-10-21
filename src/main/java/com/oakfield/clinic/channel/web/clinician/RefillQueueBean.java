package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Prescription;
import com.oakfield.clinic.svc.PrescriptionService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Clinician refill decision queue. Notes are shared across rows (JSF
 * ui:repeat cannot bind per-row inputs to one bean field): approveNote,
 * denyNote and needsInfoQuestion apply to whichever row is acted on.
 */
@Named("refillQueue")
@ViewScoped
public class RefillQueueBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private PrescriptionService prescriptions;

    private List<Prescription> queue = Collections.emptyList();
    private String approveNote;
    private String denyNote;
    private String needsInfoQuestion;
    private long selectedId;

    public void load() {
        queue = prescriptions.refillQueue(session.ctx());
    }

    public void approve(long id) {
        try {
            prescriptions.approveRefill(session.ctx(), id, approveNote);
            approveNote = null;
            FacesUtil.info("Refill approved", "The refill was approved and sent to the pharmacy.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not approve the refill", e.getMessage());
        }
        selectedId = id;
        load();
    }

    public void deny(long id) {
        try {
            prescriptions.denyRefill(session.ctx(), id, denyNote);
            denyNote = null;
            FacesUtil.info("Refill denied", "The patient has been notified.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not deny the refill", e.getMessage());
        }
        selectedId = id;
        load();
    }

    public void needsInfo(long id) {
        try {
            prescriptions.needsInfoRefill(session.ctx(), id, needsInfoQuestion);
            needsInfoQuestion = null;
            FacesUtil.info("More information requested", "The patient has been asked for more information.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not request more information", e.getMessage());
        }
        selectedId = id;
        load();
    }

    public List<Prescription> getQueue() { return queue; }

    public String getApproveNote() { return approveNote; }
    public void setApproveNote(String approveNote) { this.approveNote = approveNote; }

    public String getDenyNote() { return denyNote; }
    public void setDenyNote(String denyNote) { this.denyNote = denyNote; }

    public String getNeedsInfoQuestion() { return needsInfoQuestion; }
    public void setNeedsInfoQuestion(String needsInfoQuestion) { this.needsInfoQuestion = needsInfoQuestion; }

    public long getSelectedId() { return selectedId; }
    public void setSelectedId(long selectedId) { this.selectedId = selectedId; }
}
