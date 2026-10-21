package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.AuditEvent;
import com.oakfield.clinic.svc.AuditService;
import com.oakfield.clinic.svc.PageResult;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Clinic-admin audit log viewer: filter by date/action/target/actor, paged. Append-only. */
@Named("adminAudit")
@ViewScoped
public class AdminAuditBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AuditService audit;

    private LocalDate from;
    private LocalDate to;
    private String action;
    private String targetType;
    private String actor = "";
    private int pageIndex;

    private List<String> actions = List.of();

    @PostConstruct
    public void load() {
        actions = audit.distinctActions();
    }

    public PageResult<AuditEvent> getPage() {
        AuditService.AuditFilter filter = new AuditService.AuditFilter(
                from, to, blankToNull(action), blankToNull(targetType), actor);
        return new PageResult<>(audit.search(filter, pageIndex, 25),
                audit.count(filter), pageIndex, 25);
    }

    public void filter() {
        pageIndex = 0;
    }

    public void clear() {
        from = null;
        to = null;
        action = null;
        targetType = null;
        actor = "";
        pageIndex = 0;
    }

    public void nextPage() {
        pageIndex++;
    }

    public void prevPage() {
        if (pageIndex > 0) pageIndex--;
    }

    public List<String> getTargetTypes() {
        return List.of("PATIENT", "APPOINTMENT", "VISIT_SUMMARY", "PRESCRIPTION", "DOCUMENT",
                "MESSAGE_THREAD", "USER", "EXPORT_JOB", "IMPORT_JOB", "WEBHOOK", "CLINIC_SETTING");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public List<String> getActions() { return actions; }
}
