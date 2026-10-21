package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Prescription;
import com.oakfield.clinic.svc.PrescriptionService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/** Patient's active prescriptions and the self-service refill flow. */
@Named("ptPrescriptions")
@ViewScoped
public class PatientPrescriptionsBean implements Serializable {

    private List<Prescription> items;
    private String refillNote;
    private Long selectedRxId;

    @Inject
    private SessionInfo session;

    @Inject
    private PrescriptionService prescriptions;

    @PostConstruct
    public void load() {
        items = prescriptions.forPatientHome(session.ctx());
    }

    public void requestRefill(long id) {
        selectedRxId = id;
        try {
            prescriptions.requestRefill(session.ctx(), id, refillNote);
            FacesUtil.info("Refill requested", "Your provider has been notified.");
            refillNote = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not request refill", e.getMessage());
        }
    }

    public void cancelRefill(long id) {
        try {
            prescriptions.cancelRefill(session.ctx(), id);
            FacesUtil.info("Request withdrawn", "Your refill request was cancelled.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not cancel", e.getMessage());
        }
    }

    public String statusLabel(Prescription rx) {
        if (rx.getStatus() == null) return "";
        return switch (rx.getStatus()) {
            case ACTIVE -> "Active";
            case DISCONTINUED -> "Discontinued";
            case COMPLETED -> "Completed";
        };
    }

    public String transmitLabel(Prescription rx) {
        if (rx.getRefillTransmitStatus() == null) return "";
        return switch (rx.getRefillTransmitStatus()) {
            case PENDING -> "Awaiting pharmacy transmission";
            case SENT -> "Sent to pharmacy";
            case FAILED -> "Pharmacy transmission failed";
        };
    }

    public List<Prescription> getItems() { return items; }
    public String getRefillNote() { return refillNote; }
    public void setRefillNote(String refillNote) { this.refillNote = refillNote; }
    public Long getSelectedRxId() { return selectedRxId; }
    public void setSelectedRxId(Long selectedRxId) { this.selectedRxId = selectedRxId; }
}
