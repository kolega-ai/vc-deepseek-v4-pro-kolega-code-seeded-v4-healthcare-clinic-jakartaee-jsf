package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.model.DocumentCategory;
import com.oakfield.clinic.model.DocumentStatus;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.PageResult;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.Serializable;

/** Patient documents: upload forms and browse the record (own patient only). */
@Named("ptDocuments")
@ViewScoped
public class PatientDocumentsBean implements Serializable {

    private static final int PAGE_SIZE = 20;

    /** Patients only upload forms; the category is fixed. */
    private static final DocumentCategory CATEGORY = DocumentCategory.FORM;

    private DocumentStatus statusFilter;
    private String query;
    private int pageIndex;
    private PageResult<Document> page;

    private Part file;
    private String title;
    private String description;

    @Inject
    private SessionInfo session;

    @Inject
    private DocumentService documents;

    @Inject
    private PatientAccessService patientAccess;

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
        // patientId is left null: the service forces the patient's own record.
        page = documents.search(session.ctx(),
                new DocumentService.DocFilter(null, CATEGORY, statusFilter, query), pageIndex, PAGE_SIZE);
    }

    public void upload() {
        try {
            long patientId = patientAccess.patientForUser(session.ctx()).getId();
            documents.upload(session.ctx(), patientId, CATEGORY, title, description, file);
            FacesUtil.info("Uploaded", "Your form was uploaded and is awaiting review.");
            title = null;
            description = null;
            file = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Upload failed", e.getMessage());
        }
    }

    public DocumentCategory getCategoryFilter() { return CATEGORY; }
    public DocumentStatus[] getStatusOptions() { return DocumentStatus.values(); }
    public PageResult<Document> getPage() { return page; }
    public int getPageIndex() { return pageIndex; }
    public DocumentStatus getStatusFilter() { return statusFilter; }
    public void setStatusFilter(DocumentStatus statusFilter) { this.statusFilter = statusFilter; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public Part getFile() { return file; }
    public void setFile(Part file) { this.file = file; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
