package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Staff appointment detail: full record, patient summary, and status-specific
 * actions (confirm, check-in, complete, cancel, no-show, reschedule).
 */
@Named("staffAppointment")
@ViewScoped
public class StaffAppointmentBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;
    @Inject
    private PatientService patients;

    private long id = -1;
    private Appointment appointment;
    private Patient patient;

    private String cancelReason;
    private String noShowReason;

    private LocalDate rescheduleDate;
    private List<LocalDateTime> rescheduleSlots = List.of();
    private LocalDateTime rescheduleSlot;

    @PostConstruct
    public void load() {
        id = FacesUtil.paramLong("id", -1);
        appointment = null;
        patient = null;
        rescheduleSlots = List.of();
        rescheduleSlot = null;
        if (id < 0) {
            return;
        }
        appointment = appointments.get(session.ctx(), id);
        patient = patients.getForStaff(session.ctx(), appointment.getPatient().getId());
        rescheduleDate = appointment.getStartTime().toLocalDate();
    }

    public boolean isPastStart() {
        return appointment != null && appointment.getStartTime().isBefore(LocalDateTime.now());
    }

    public void confirm() {
        try {
            appointments.confirm(session.ctx(), id);
            FacesUtil.info("Confirmed", "The appointment was confirmed and the patient notified.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not confirm", e.getMessage());
        }
    }

    public void checkIn() {
        try {
            appointments.checkIn(session.ctx(), id);
            FacesUtil.info("Checked in", "The patient was checked in.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Check-in failed", e.getMessage());
        }
    }

    public void complete() {
        try {
            appointments.complete(session.ctx(), id);
            FacesUtil.info("Completed", "The appointment was marked completed.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not complete", e.getMessage());
        }
    }

    public void cancel() {
        try {
            appointments.cancel(session.ctx(), id, cancelReason);
            cancelReason = null;
            FacesUtil.info("Cancelled", "The appointment was cancelled and the patient notified.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not cancel", e.getMessage());
        }
    }

    public void markNoShow() {
        try {
            appointments.markNoShow(session.ctx(), id, noShowReason);
            noShowReason = null;
            FacesUtil.info("Marked no-show", "The appointment was marked as a no-show.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not mark no-show", e.getMessage());
        }
    }

    public void loadSlots() {
        rescheduleSlots = List.of();
        rescheduleSlot = null;
        try {
            rescheduleSlots = appointments.availableSlots(session.ctx(),
                    appointment.getProvider().getId(), rescheduleDate, appointment.getType().getId());
            if (rescheduleSlots.isEmpty()) {
                FacesUtil.warn("No availability", "No open slots on that date; try another day.");
            }
        } catch (ValidationException e) {
            FacesUtil.error("Could not load slots", e.getMessage());
        }
    }

    public void reschedule() {
        if (rescheduleSlot == null) {
            FacesUtil.error("Reschedule", "Pick a new time slot first.");
            return;
        }
        try {
            appointments.reschedule(session.ctx(), id, rescheduleSlot);
            FacesUtil.info("Rescheduled", "The appointment was moved to the new time.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not reschedule", e.getMessage());
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getNoShowReason() {
        return noShowReason;
    }

    public void setNoShowReason(String noShowReason) {
        this.noShowReason = noShowReason;
    }

    public LocalDate getRescheduleDate() {
        return rescheduleDate;
    }

    public void setRescheduleDate(LocalDate rescheduleDate) {
        this.rescheduleDate = rescheduleDate;
    }

    public List<LocalDateTime> getRescheduleSlots() {
        return rescheduleSlots;
    }

    public LocalDateTime getRescheduleSlot() {
        return rescheduleSlot;
    }

    public void setRescheduleSlot(LocalDateTime rescheduleSlot) {
        this.rescheduleSlot = rescheduleSlot;
    }
}
