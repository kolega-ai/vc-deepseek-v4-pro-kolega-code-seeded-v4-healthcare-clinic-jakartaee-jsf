package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.model.VisitSummary;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import com.oakfield.clinic.svc.VisitService;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** The clinician's daily work queue: today's appointments plus draft visit summaries. */
@Named("clinicianQueue")
@ViewScoped
public class ClinicianQueueBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private ProviderService providers;
    @Inject
    private AppointmentService appointments;
    @Inject
    private VisitService visits;

    private Provider myProvider;
    private List<Appointment> myToday = Collections.emptyList();
    private List<VisitSummary> drafts = Collections.emptyList();

    public void load() {
        myProvider = providers.byUserId(session.ctx().getUserId());
        if (myProvider != null) {
            myToday = appointments.todayForProvider(session.ctx(), myProvider.getId());
            drafts = visits.draftsByProvider(session.ctx(), myProvider.getId());
        }
    }

    /** Mark a SCHEDULED / CHECKED_IN appointment as COMPLETED. */
    public void complete(long id) {
        try {
            appointments.complete(session.ctx(), id);
            FacesUtil.info("Visit completed",
                    "The appointment was marked complete. You can now write the visit summary.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not complete the appointment", e.getMessage());
        }
        load();
    }

    public Provider getMyProvider() { return myProvider; }

    public List<Appointment> getMyToday() { return myToday; }

    public List<VisitSummary> getDrafts() { return drafts; }
}
