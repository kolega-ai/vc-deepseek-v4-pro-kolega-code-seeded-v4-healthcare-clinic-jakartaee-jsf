package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.PageResult;
import com.oakfield.clinic.svc.ProviderService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * Staff appointment search list with date/provider/status filters and an optional
 * patient context carried in from the patient profile page (?patient=).
 */
@Named("staffAppointments")
@ViewScoped
public class StaffAppointmentsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentService appointments;
    @Inject
    private ProviderService providers;

    private LocalDate from;
    private LocalDate to;
    private Long providerId;
    private Long patientId;
    private AppointmentStatus status;
    private int pageIndex;
    private PageResult<Appointment> page;

    @PostConstruct
    public void load() {
        long p = FacesUtil.paramLong("patient", -1);
        patientId = p >= 0 ? p : null;
        refresh();
    }

    private void refresh() {
        page = appointments.search(session.ctx(),
                new AppointmentService.AppointmentFilter(from, to, providerId, patientId, status),
                pageIndex, 20);
    }

    public void filter() {
        pageIndex = 0;
        refresh();
    }

    public void clear() {
        from = null;
        to = null;
        providerId = null;
        status = null;
        pageIndex = 0;
        refresh();
    }

    public void nextPage() {
        pageIndex++;
        refresh();
    }

    public void prevPage() {
        if (pageIndex > 0) {
            pageIndex--;
            refresh();
        }
    }

    public List<AppointmentStatus> getStatuses() {
        return List.of(AppointmentStatus.values());
    }

    public List<Provider> getProviders() {
        return providers.listActive();
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public PageResult<Appointment> getPage() {
        return page;
    }
}
