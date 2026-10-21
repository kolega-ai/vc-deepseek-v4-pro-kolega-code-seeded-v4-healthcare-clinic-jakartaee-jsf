package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.ImportJob;
import com.oakfield.clinic.svc.ImportExportService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/** Clinic-admin patient CSV import: upload a file, review recent job results and error reports. */
@Named("adminImports")
@ViewScoped
public class AdminImportsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private ImportExportService importExport;

    private Part file;
    private Part previewFile;
    private String previewHtml;
    private List<ImportJob> recentJobs;

    @PostConstruct
    public void load() {
        recentJobs = importExport.recentImports(15);
    }

    public void importCsv() {
        if (file == null || file.getSize() == 0) {
            FacesUtil.error("No file selected", "Pick a CSV file before importing.");
            return;
        }
        try {
            ImportJob job = importExport.importPatients(session.ctx(),
                    file.getSubmittedFileName(), file.getInputStream());
            FacesUtil.info("Import finished", job.getOriginalName() + ": " + job.getSuccessRows()
                    + " imported, " + job.getErrorRows() + " errors.");
            file = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Import failed", e.getMessage());
        } catch (IOException e) {
            FacesUtil.error("Import failed", "Could not read the uploaded file.");
        }
    }

    /** Show the first row of the selected CSV so the operator can confirm the columns line up. */
    public void preview() {
        if (previewFile == null || previewFile.getSize() == 0) {
            FacesUtil.error("No file selected", "Pick a CSV file to preview.");
            return;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(previewFile.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                FacesUtil.error("Empty file", "The selected CSV has no rows.");
                return;
            }
            previewHtml = "<div class=\"cell-strong\">" + firstLine.replace(",", "</div><div>") + "</div>";
        } catch (IOException e) {
            FacesUtil.error("Preview failed", "Could not read the selected file.");
        }
    }

    public Part getFile() { return file; }
    public void setFile(Part file) { this.file = file; }
    public Part getPreviewFile() { return previewFile; }
    public void setPreviewFile(Part previewFile) { this.previewFile = previewFile; }
    public String getPreviewHtml() { return previewHtml; }
    public List<ImportJob> getRecentJobs() { return recentJobs; }
}
