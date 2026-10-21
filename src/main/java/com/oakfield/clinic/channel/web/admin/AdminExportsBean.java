package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.ExportJob;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.ImportExportService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Clinic-admin CSV exports: schedule, visit summaries, and audit log. */
@Named("adminExports")
@ViewScoped
public class AdminExportsBean implements Serializable {

    private static final int MAX_RANGE_DAYS = 93;

    @Inject
    private SessionInfo session;
    @Inject
    private ImportExportService importExport;
    @Inject
    private ProviderService providers;

    private List<Provider> providerList;
    private List<ExportJob> recent;

    // schedule export
    private LocalDate from;
    private LocalDate to;
    private Long providerId;
    private String status;

    // summaries export
    private LocalDate sumFrom;
    private LocalDate sumTo;
    private Long sumProviderId;

    // audit export
    private LocalDate audFrom;
    private LocalDate audTo;
    private String audAction;

    @PostConstruct
    public void load() {
        providerList = providers.listAll();
        recent = importExport.recentExports(session.ctx());
    }

    public void exportScheduleCsv() {
        if (!validRange(from, to)) return;
        try {
            AppointmentStatus s = status == null || status.isBlank() ? null : AppointmentStatus.valueOf(status);
            importExport.exportSchedule(session.ctx(),
                    new ImportExportService.ExportFilter(from, to, providerId, s));
            FacesUtil.info("Export ready", "The schedule export appears in the list below.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not run export", e.getMessage());
        }
    }

    public void exportSummariesCsv() {
        if (!validRange(sumFrom, sumTo)) return;
        try {
            importExport.exportSummaries(session.ctx(), sumFrom, sumTo, sumProviderId);
            FacesUtil.info("Export ready", "The visit summary export appears in the list below.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not run export", e.getMessage());
        }
    }

    public void exportAuditCsv() {
        if (!validRange(audFrom, audTo)) return;
        try {
            importExport.exportAudit(session.ctx(), audFrom, audTo, audAction);
            FacesUtil.info("Export ready", "The audit export appears in the list below.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not run export", e.getMessage());
        }
    }

    private boolean validRange(LocalDate a, LocalDate b) {
        if (a == null || b == null) {
            FacesUtil.error("Date range required", "Pick both a start and an end date.");
            return false;
        }
        if (b.isBefore(a)) {
            FacesUtil.error("Invalid date range", "End date must be on or after the start date.");
            return false;
        }
        if (b.isAfter(a.plusDays(MAX_RANGE_DAYS))) {
            FacesUtil.error("Range too large", "Exports are limited to " + MAX_RANGE_DAYS + " days at a time.");
            return false;
        }
        return true;
    }

    public AppointmentStatus[] getStatuses() {
        return AppointmentStatus.values();
    }

    public List<Provider> getProviderList() { return providerList; }
    public List<ExportJob> getRecent() { return recent; }
    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getSumFrom() { return sumFrom; }
    public void setSumFrom(LocalDate sumFrom) { this.sumFrom = sumFrom; }
    public LocalDate getSumTo() { return sumTo; }
    public void setSumTo(LocalDate sumTo) { this.sumTo = sumTo; }
    public Long getSumProviderId() { return sumProviderId; }
    public void setSumProviderId(Long sumProviderId) { this.sumProviderId = sumProviderId; }
    public LocalDate getAudFrom() { return audFrom; }
    public void setAudFrom(LocalDate audFrom) { this.audFrom = audFrom; }
    public LocalDate getAudTo() { return audTo; }
    public void setAudTo(LocalDate audTo) { this.audTo = audTo; }
    public String getAudAction() { return audAction; }
    public void setAudAction(String audAction) { this.audAction = audAction; }
}
