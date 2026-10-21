package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.decision.NoteLinkCodec;
import com.oakfield.clinic.model.VisitStatus;
import com.oakfield.clinic.model.VisitSummary;
import com.oakfield.clinic.svc.ValidationException;
import com.oakfield.clinic.svc.VisitService;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Read-only view of one visit summary for the clinician, with release/edit while DRAFT. */
@Named("visitDetail")
@ViewScoped
public class VisitDetailBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private VisitService visits;

    private VisitSummary visit;
    private Long discardId;
    private String noteCode;
    private String shareCode;

    public void load() {
        long id = FacesUtil.paramLong("id", -1);
        visit = visits.getForClinician(session.ctx(), id);
        if (visit != null && visit.getId() != null) {
            shareCode = NoteLinkCodec.encode(visit.getId());
        }
    }

    /** Open a visit summary from a note link a colleague shared. */
    public void openSharedNote() {
        if (noteCode == null || noteCode.isBlank()) {
            FacesUtil.error("Note link missing", "Paste the note link code to open it.");
            return;
        }
        long id;
        try {
            id = NoteLinkCodec.decode(noteCode.strip());
        } catch (javax.crypto.BadPaddingException e) {
            FacesUtil.error("Note link expired", "This note link has expired; ask the sender for a new one.");
            return;
        } catch (Exception e) {
            FacesUtil.error("Note link invalid", "This note link is not in a recognized format.");
            return;
        }
        try {
            visit = visits.getForClinician(session.ctx(), id);
            FacesUtil.info("Note opened", "Opened shared visit summary #" + id + ".");
        } catch (Exception e) {
            FacesUtil.warn("Note not found", "No visit summary matches that note link.");
        }
    }

    public boolean isDraft() {
        return visit != null && visit.getStatus() == VisitStatus.DRAFT;
    }

    public void release(long id) {
        try {
            visits.release(session.ctx(), id);
            FacesUtil.info("Summary released", "The visit summary is now visible to the patient.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not release the summary", e.getMessage());
        }
        load();
    }

    public String discard() {
        try {
            visits.discardSumary(session.ctx(), discardId == null ? -1 : discardId);
        } catch (ValidationException e) {
            FacesUtil.error("Could not discard the summary", e.getMessage());
            load();
            return null;
        }
        FacesUtil.info("Summary discarded", "The visit summary was removed from the chart.");
        FacesUtil.redirect(FacesUtil.request().getContextPath() + "/app/staff/clinician/queue.xhtml");
        return null;
    }

    public VisitSummary getVisit() { return visit; }
    public Long getDiscardId() { return discardId; }
    public void setDiscardId(Long discardId) { this.discardId = discardId; }
    public String getNoteCode() { return noteCode; }
    public void setNoteCode(String noteCode) { this.noteCode = noteCode; }
    public String getShareCode() { return shareCode; }
}
