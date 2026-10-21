package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.AppointmentTypeService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Detail of one of the patient's own appointments: cancel + self-service reschedule. */
@Named("ptAppointment")
@ViewScoped
public class PatientAppointmentBean implements Serializable {

    private long id;
    private Appointment appt;

    private String cancelReason;

    private Long providerId;
    private Long typeId;
    private LocalDate slotDate;
    private LocalDateTime newStart;
    private String rescheduleReason;
    private List<LocalDateTime> slots = List.of();

    @Inject
    private SessionInfo session;

    @Inject
    private AppointmentService appointments;

    @Inject
    private ProviderService providers;

    @Inject
    private AppointmentTypeService types;

    @PostConstruct
    public void load() {
        id = FacesUtil.paramLong("id", -1);
        reload();
    }

    private void reload() {
        if (id <= 0) {
            appt = null;
            return;
        }
        appt = appointments.get(session.ctx(), id);
        providerId = appt.getProvider().getId();
        typeId = appt.getType().getId();
        slotDate = appt.getStartTime().toLocalDate();
        newStart = null;
        rescheduleReason = null;
        slots = List.of();
    }

    public void cancel() {
        try {
            appointments.cancel(session.ctx(), id, cancelReason);
            FacesUtil.info("Appointment cancelled", "The clinic has been notified.");
            cancelReason = null;
            reload();
        } catch (ValidationException e) {
            FacesUtil.error("Could not cancel", e.getMessage());
        }
    }

    public void reschedule() {
        if (providerId == null || typeId == null || newStart == null) {
            FacesUtil.error("Incomplete reschedule", "Choose a provider, a type, and a new time slot.");
            return;
        }
        try {
            appointments.reschedulePatient(session.ctx(), id, providerId, typeId, newStart, rescheduleReason);
            FacesUtil.info("Reschedule requested", "Your new appointment request was sent to the clinic.");
            reload();
        } catch (ValidationException e) {
            FacesUtil.error("Could not reschedule", e.getMessage());
        }
    }

    /** Cached slots for the reschedule picker; filled by {@link #loadSlots()}. */
    public List<LocalDateTime> availableSlots() { return slots; }

    /** Loads the available slots for the current provider/type/date selection. */
    public void loadSlots() {
        if (providerId == null || typeId == null || slotDate == null) {
            slots = List.of();
            return;
        }
        try {
            slots = appointments.availableSlots(session.ctx(), providerId, slotDate, typeId);
        } catch (ValidationException e) {
            slots = List.of();
            FacesUtil.error("No slots", e.getMessage());
        }
    }

    public List<Provider> getProviders() { return providers.listActive(); }

    public List<AppointmentType> getTypes() { return types.listActive(); }

    public boolean isCanReschedule() {
        if (appt == null) return false;
        AppointmentStatus s = appt.getStatus();
        return (s == AppointmentStatus.SCHEDULED || s == AppointmentStatus.REQUESTED)
                && appt.getStartTime().isAfter(LocalDateTime.now());
    }

    public boolean isCanCancel() {
        if (appt == null) return false;
        AppointmentStatus s = appt.getStatus();
        return s == AppointmentStatus.SCHEDULED || s == AppointmentStatus.REQUESTED;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public Appointment getAppt() { return appt; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public LocalDate getSlotDate() { return slotDate; }
    public void setSlotDate(LocalDate slotDate) { this.slotDate = slotDate; }
    public LocalDateTime getNewStart() { return newStart; }
    public void setNewStart(LocalDateTime newStart) { this.newStart = newStart; }
    public String getRescheduleReason() { return rescheduleReason; }
    public void setRescheduleReason(String rescheduleReason) { this.rescheduleReason = rescheduleReason; }
}
