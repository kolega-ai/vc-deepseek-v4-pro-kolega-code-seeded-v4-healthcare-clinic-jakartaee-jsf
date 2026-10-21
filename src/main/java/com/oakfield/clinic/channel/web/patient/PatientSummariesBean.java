package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.VisitSummary;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.VisitService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/** Released visit summaries for the patient's own record. */
@Named("ptSummaries")
@ViewScoped
public class PatientSummariesBean implements Serializable {

    private List<VisitSummary> summaries;
    private Long selectedId;

    @Inject
    private SessionInfo session;

    @Inject
    private VisitService visits;

    @Inject
    private PatientAccessService patientAccess;

    @PostConstruct
    public void load() {
        summaries = visits.releasedForPatient(session.ctx(), 0);
        // Deep link support (?id=): only open it when it is in the patient's own released list.
        long paramId = FacesUtil.paramLong("id", -1);
        if (paramId > 0) {
            for (VisitSummary v : summaries) {
                if (v.getId() != null && v.getId() == paramId) {
                    selectedId = paramId;
                    break;
                }
            }
        }
    }

    public VisitSummary getSelected() {
        if (selectedId == null) return null;
        return visits.getForPatient(session.ctx(), selectedId);
    }

    public void open(long id) {
        selectedId = id;
    }

    /** The chart the pocket card should follow by default. */
    public long getOwnChartId() {
        return patientAccess.patientForUser(session.ctx()).getId();
    }

    public List<VisitSummary> getSummaries() { return summaries; }
    public Long getSelectedId() { return selectedId; }
    public void setSelectedId(Long selectedId) { this.selectedId = selectedId; }
}
