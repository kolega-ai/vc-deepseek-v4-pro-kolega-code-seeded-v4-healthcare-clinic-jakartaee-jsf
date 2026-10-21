package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.PageResult;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;

/** Patient's own appointment list with status/date filters. */
@Named("ptAppointments")
@ViewScoped
public class PatientAppointmentsBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    private AppointmentStatus status;
    private LocalDate from;
    private LocalDate to;
    private int pageIndex;
    private PageResult<Appointment> page;

    @Inject
    private SessionInfo session;

    @Inject
    private AppointmentService appointments;

    @PostConstruct
    public void load() {
        pageIndex = 0;
        refresh();
    }

    public void filter() {
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

    private void refresh() {
        // A null status means "live upcoming": default the window to today onwards.
        LocalDate effectiveFrom = from;
        if (status == null && effectiveFrom == null) {
            effectiveFrom = LocalDate.now();
        }
        // patientId is left null: the service forces the patient's own record.
        page = appointments.search(session.ctx(),
                new AppointmentService.AppointmentFilter(effectiveFrom, to, null, null, status),
                pageIndex, PAGE_SIZE);
    }

    public AppointmentStatus[] getStatusOptions() { return AppointmentStatus.values(); }

    public PageResult<Appointment> getPage() { return page; }
    public int getPageIndex() { return pageIndex; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
}
