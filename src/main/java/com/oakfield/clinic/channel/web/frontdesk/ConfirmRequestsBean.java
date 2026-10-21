package com.oakfield.clinic.channel.web.frontdesk;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** Front desk queue of REQUESTED appointments to confirm or cancel. */
@Named("confirmRequests")
@ViewScoped
public class ConfirmRequestsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;

    private List<Appointment> list = Collections.emptyList();
    private String cancelReason;

    @PostConstruct
    public void load() {
        list = appointments.requestedQueue(session.ctx());
    }

    public void confirm(long id) {
        try {
            appointments.confirm(session.ctx(), id);
            FacesUtil.info("Appointment confirmed", "The request was confirmed and the patient was notified.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not confirm the request", e.getMessage());
        }
        load();
    }

    public void cancelRequest(long id) {
        try {
            appointments.cancel(session.ctx(), id, cancelReason);
            cancelReason = null;
            FacesUtil.info("Request cancelled", "The appointment request was cancelled.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not cancel the request", e.getMessage());
        }
        load();
    }

    public List<Appointment> getList() { return list; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
