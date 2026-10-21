package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.AppointmentTypeService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Multi-step patient booking wizard: type → provider → date+slot → reason+confirm. */
@Named("booking")
@ViewScoped
public class PatientBookingBean implements Serializable {

    public static final int STEP_TYPE = 1;
    public static final int STEP_PROVIDER = 2;
    public static final int STEP_SLOT = 3;
    public static final int STEP_CONFIRM = 4;

    private int step = STEP_TYPE;
    private Long typeId;
    private Long providerId;
    private LocalDate date;
    private LocalDateTime start;
    private String reason;
    private String notes;
    private List<LocalDateTime> slots = List.of();

    @Inject
    private SessionInfo session;

    @Inject
    private AppointmentService appointments;

    @Inject
    private AppointmentTypeService types;

    @Inject
    private ProviderService providers;

    @Inject
    private PatientAccessService patientAccess;

    /** Fresh page load starts the wizard at step 1. */
    public void load() {
        goTo(STEP_TYPE);
    }

    public String getTypeName() {
        if (typeId == null) return "";
        return types.listActive().stream()
                .filter(t -> t.getId().equals(typeId))
                .findFirst()
                .map(AppointmentType::getName)
                .orElse("");
    }

    public String getProviderName() {
        if (providerId == null) return "";
        return providers.listActive().stream()
                .filter(p -> p.getId().equals(providerId))
                .findFirst()
                .map(Provider::fullName)
                .orElse("");
    }

    public List<AppointmentType> getTypes() { return types.listActive(); }

    public List<Provider> getProviders() { return providers.listActive(); }

    /** Cached slot list; filled by {@link #loadSlots()}. */
    public List<LocalDateTime> getSlots() { return slots; }

    public void loadSlots() {
        if (providerId == null || typeId == null || date == null) {
            slots = List.of();
            return;
        }
        try {
            slots = appointments.availableSlots(session.ctx(), providerId, date, typeId);
        } catch (ValidationException e) {
            slots = List.of();
            FacesUtil.error("No slots available", e.getMessage());
        }
    }

    public void choose(long typeId) {
        this.typeId = typeId;
        slots = List.of();
        step = STEP_PROVIDER;
    }

    public void chooseProvider(long providerId) {
        this.providerId = providerId;
        slots = List.of();
        step = STEP_SLOT;
    }

    public void chooseSlot(LocalDateTime slot) {
        this.start = slot;
        step = STEP_CONFIRM;
    }

    public void goTo(int step) {
        this.step = Math.max(STEP_TYPE, Math.min(STEP_CONFIRM, step));
    }

    public void back() {
        goTo(step - 1);
    }

    public void confirm() {
        if (providerId == null || typeId == null || start == null) {
            FacesUtil.error("Incomplete booking", "Go back and complete the previous steps.");
            goTo(STEP_TYPE);
            return;
        }
        try {
            long patientId = patientAccess.patientForUser(session.ctx()).getId();
            appointments.bookRequested(session.ctx(), patientId, providerId, typeId, start, reason, notes);
            FacesUtil.info("Request sent", "Your appointment request was sent to the clinic for confirmation.");
            FacesUtil.redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath()
                    + "/app/patient/appointments.xhtml?faces-redirect=true");
        } catch (ValidationException e) {
            FacesUtil.error("Could not book", e.getMessage());
        }
    }

    public int getStep() { return step; }
    public void setStep(int step) { this.step = step; }
    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalDateTime getStart() { return start; }
    public void setStart(LocalDateTime start) { this.start = start; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
