package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff schedule board: a day view grouped by provider with quick per-appointment
 * actions. Every mutation posts to the service layer, which re-checks scope server-side.
 */
@Named("staffSchedule")
@ViewScoped
public class StaffScheduleBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;
    @Inject
    private ProviderService providers;

    private LocalDate day = LocalDate.now();
    private Long providerFilter;
    private List<Appointment> board = new ArrayList<>();
    private List<Provider> allProviders = new ArrayList<>();

    /** One shared reason input per row action: each row posts its own form. */
    private String noShowReason;
    private String cancelReason;
    private Long selectedId;

    /** A provider and their appointments for the selected day. */
    public record ProviderBoard(Provider provider, List<Appointment> appointments) { }

    @PostConstruct
    public void load() {
        refresh();
    }

    private void refresh() {
        board = appointments.forDay(session.ctx(), day, providerFilter);
        allProviders = providers.listActive();
    }

    public List<Appointment> getAppointments() {
        return board;
    }

    public List<Provider> getProviders() {
        return allProviders;
    }

    /** The day board grouped by provider so the page can render one card per provider. */
    public List<ProviderBoard> getDayBoard() {
        Map<Long, List<Appointment>> byProvider = new LinkedHashMap<>();
        for (Appointment a : board) {
            byProvider.computeIfAbsent(a.getProvider().getId(), k -> new ArrayList<>()).add(a);
        }
        List<Provider> visible = new ArrayList<>();
        if (providerFilter != null) {
            for (Provider p : allProviders) {
                if (p.getId().equals(providerFilter)) {
                    visible.add(p);
                }
            }
        } else {
            visible.addAll(allProviders);
        }
        List<ProviderBoard> result = new ArrayList<>();
        for (Provider p : visible) {
            result.add(new ProviderBoard(p, byProvider.getOrDefault(p.getId(), List.of())));
        }
        return result;
    }

    public void prevDay() {
        day = day.minusDays(1);
        refresh();
    }

    public void nextDay() {
        day = day.plusDays(1);
        refresh();
    }

    public void today() {
        day = LocalDate.now();
        refresh();
    }

    public void applyFilter() {
        refresh();
    }

    public boolean isPastStart(Appointment a) {
        return a != null && a.getStartTime().isBefore(LocalDateTime.now());
    }

    public void checkIn(long id) {
        try {
            appointments.checkIn(session.ctx(), id);
            FacesUtil.info("Checked in", "The patient was checked in.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Check-in failed", e.getMessage());
        }
    }

    public void complete(long id) {
        try {
            appointments.complete(session.ctx(), id);
            FacesUtil.info("Completed", "The appointment was marked completed.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not complete", e.getMessage());
        }
    }

    public void markNoShow(long id) {
        try {
            appointments.markNoShow(session.ctx(), id, noShowReason);
            selectedId = id;
            noShowReason = null;
            FacesUtil.info("Marked no-show", "The appointment was marked as a no-show.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not mark no-show", e.getMessage());
        }
    }

    public void cancel(long id) {
        try {
            appointments.cancel(session.ctx(), id, cancelReason);
            cancelReason = null;
            FacesUtil.info("Cancelled", "The appointment was cancelled.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not cancel", e.getMessage());
        }
    }

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public Long getProviderFilter() {
        return providerFilter;
    }

    public void setProviderFilter(Long providerFilter) {
        this.providerFilter = providerFilter;
    }

    public String getNoShowReason() {
        return noShowReason;
    }

    public void setNoShowReason(String noShowReason) {
        this.noShowReason = noShowReason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getSelectedId() {
        return selectedId;
    }

    public void setSelectedId(Long selectedId) {
        this.selectedId = selectedId;
    }
}
