package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.model.DocumentCategory;
import com.oakfield.clinic.model.DocumentStatus;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.IntegrationService;
import com.oakfield.clinic.svc.PageResult;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.Serializable;
import java.util.List;

/**
 * Staff documents: upload forms/clinical files, filter the library, and review
 * (verify / reject / archive) submitted forms.
 */
@Named("staffDocuments")
@ViewScoped
public class StaffDocumentsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private DocumentService documents;
    @Inject
    private PatientService patients;
    @Inject
    private IntegrationService integrations;

    private Long patientId;
    private DocumentCategory category;
    private DocumentStatus status;
    private String query;
    private int pageIndex;
    private PageResult<Document> page;

    private Part file;
    private String title;
    private String description;
    private DocumentCategory uploadCategory;
    private Long uploadPatientId;
    private String reviewNote;
    private Long scanPatientId;
    private Part scanFile;
    private String faxNumber;
    private Part faxFile;

    @PostConstruct
    public void load() {
        long p = FacesUtil.paramLong("patient", -1);
        patientId = p >= 0 ? p : null;
        refresh();
    }

    private void refresh() {
        page = documents.search(session.ctx(),
                new DocumentService.DocFilter(patientId, category, status, query), pageIndex, 20);
    }

    public void filter() {
        pageIndex = 0;
        refresh();
    }

    public void clear() {
        patientId = null;
        category = null;
        status = null;
        query = null;
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

    /** First 50 active patients for the upload form's patient picker. */
    public List<Patient> getUploadPatients() {
        return patients.search(session.ctx(),
                new PatientService.PatientFilter(null, null, true), 0, 50, "name").items();
    }

    public void upload() {
        try {
            if (uploadPatientId == null) {
                throw new ValidationException("Choose a patient.");
            }
            documents.upload(session.ctx(), uploadPatientId, uploadCategory, title, description, file);
            FacesUtil.info("Uploaded", "The document was stored for the patient.");
            file = null;
            title = null;
            description = null;
            uploadCategory = null;
            uploadPatientId = null;
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Upload failed", e.getMessage());
        }
    }

    /** Scanner station drop: file the scanned page under the accession name the scanner assigned. */
    public void storeScan() {
        try {
            if (scanPatientId == null) {
                throw new ValidationException("Choose a patient.");
            }
            documents.storeScan(session.ctx(), scanPatientId, scanFile);
            FacesUtil.info("Scan filed", "The scanned page was filed to the chart.");
            scanFile = null;
            scanPatientId = null;
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Scan failed", e.getMessage());
        }
    }

    /** Fax a referral packet to a specialist practice through the clinic fax gateway. */
    public void faxReferral() {
        try {
            if (faxFile == null || faxFile.getSize() <= 0) {
                throw new ValidationException("Choose a document to fax.");
            }
            byte[] bytes;
            try (java.io.InputStream in = faxFile.getInputStream()) {
                bytes = in.readAllBytes();
            }
            integrations.faxReferral(session.ctx(), faxNumber, faxFile.getSubmittedFileName(), bytes);
            FacesUtil.info("Fax queued", "The document was sent to the fax gateway.");
            faxFile = null;
            faxNumber = null;
        } catch (ValidationException e) {
            FacesUtil.error("Fax failed", e.getMessage());
        } catch (java.io.IOException e) {
            FacesUtil.error("Fax failed", "Could not read the attached file.");
        }
    }

    public void verify(long id) {
        try {
            documents.verify(session.ctx(), id, reviewNote);
            reviewNote = null;
            FacesUtil.info("Verified", "The document was verified.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not verify", e.getMessage());
        }
    }

    public void reject(long id) {
        try {
            documents.reject(session.ctx(), id, reviewNote);
            reviewNote = null;
            FacesUtil.info("Rejected", "The patient was notified.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not reject", e.getMessage());
        }
    }

    public void archive(long id) {
        try {
            documents.archive(session.ctx(), id);
            FacesUtil.info("Archived", "The document was archived.");
            refresh();
        } catch (ValidationException e) {
            FacesUtil.error("Could not archive", e.getMessage());
        }
    }

    public List<DocumentCategory> getCategories() {
        return List.of(DocumentCategory.values());
    }

    public List<DocumentStatus> getStatuses() {
        return List.of(DocumentStatus.values());
    }

    public String catLabel(DocumentCategory c) {
        if (c == null) {
            return "—";
        }
        return c == DocumentCategory.FORM ? "Form" : "Clinical";
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public void setCategory(DocumentCategory category) {
        this.category = category;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public void setPageIndex(int pageIndex) {
        this.pageIndex = pageIndex;
    }

    public PageResult<Document> getPage() {
        return page;
    }

    public Part getFile() {
        return file;
    }

    public void setFile(Part file) {
        this.file = file;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DocumentCategory getUploadCategory() {
        return uploadCategory;
    }

    public void setUploadCategory(DocumentCategory uploadCategory) {
        this.uploadCategory = uploadCategory;
    }

    public Long getUploadPatientId() {
        return uploadPatientId;
    }

    public void setUploadPatientId(Long uploadPatientId) {
        this.uploadPatientId = uploadPatientId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getScanPatientId() {
        return scanPatientId;
    }

    public void setScanPatientId(Long scanPatientId) {
        this.scanPatientId = scanPatientId;
    }

    public Part getScanFile() {
        return scanFile;
    }

    public void setScanFile(Part scanFile) {
        this.scanFile = scanFile;
    }

    public String getFaxNumber() {
        return faxNumber;
    }

    public void setFaxNumber(String faxNumber) {
        this.faxNumber = faxNumber;
    }

    public Part getFaxFile() {
        return faxFile;
    }

    public void setFaxFile(Part faxFile) {
        this.faxFile = faxFile;
    }
}
