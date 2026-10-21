package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.servlet.http.Part;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Stateless
public class DocumentService {

    private static final Logger LOG = Logger.getLogger(DocumentService.class);

    /** Content types accepted per category; the extension is derived from the content type, never the client filename. */
    private static final Map<DocumentCategory, List<String>> ALLOWED_TYPES = Map.of(
            DocumentCategory.FORM, List.of("application/pdf", "image/png", "image/jpeg"),
            DocumentCategory.CLINICAL, List.of("application/pdf", "image/png", "image/jpeg", "text/plain", "text/csv"));

    private static final Map<String, String> EXTENSIONS = Map.of(
            "application/pdf", ".pdf", "image/png", ".png", "image/jpeg", ".jpg",
            "text/plain", ".txt", "text/csv", ".csv");

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientAccessService patientAccess;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    public record UploadResult(Document document, long sizeBytes) { }

    /** Upload a form or clinical document for a patient. */
    public UploadResult upload(DecisionContext ctx, long patientId, DocumentCategory category,
                               String title, String description, Part part) {
        Patient patient = patientAccess.requirePatientAccess(ctx, patientId,
                ctx.isPatient() ? PatientAccessService.AccessLevel.OWN : PatientAccessService.AccessLevel.STAFF);
        ctx.require("document.upload");
        if (category == DocumentCategory.FORM && !ctx.isPatient() && !ctx.hasScope("document.verify")) {
            // staff uploading forms is allowed only for roles that also review them; keep it simple: any staff may upload
        }
        if (part == null || part.getSize() <= 0) throw new ValidationException("Choose a file to upload.");
        int maxMb = ctx.limitInt("upload.max_mb", 10);
        if (part.getSize() > maxMb * 1024L * 1024L) {
            throw new ValidationException("File exceeds the " + maxMb + " MB limit.");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new ValidationException("Give the document a title (max 200 characters).");
        }
        if (description != null && description.length() > 1000) {
            throw new ValidationException("Description is too long (max 1000 characters).");
        }
        String contentType = part.getContentType() == null ? "" : part.getContentType().toLowerCase();
        String extension = EXTENSIONS.get(contentType);
        if (extension == null || !ALLOWED_TYPES.get(category).contains(contentType)) {
            throw new ValidationException("File type not accepted for " + category + " documents (PDF, PNG, or JPG"
                    + (category == DocumentCategory.CLINICAL ? ", TXT, CSV" : "") + ").");
        }
        // Original filename is stored for display only; sanitized and never used for storage paths.
        String originalName = sanitizeFileName(part.getSubmittedFileName());
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = FileStore.resolve(FileStore.Area.UPLOADS, storedName);
        byte[] bytes;
        try (InputStream in = part.getInputStream()) {
            bytes = in.readAllBytes();
            Files.write(target, bytes);
        } catch (IOException e) {
            LOG.errorf("Upload write failed for %s: %s", storedName, e.getMessage());
            throw new ValidationException("The upload could not be stored. Please try again.");
        }
        String sha256 = sha256Hex(bytes);

        Document d = new Document();
        d.setPatient(patient);
        d.setUploadedBy(ctx.getUserId());
        d.setCategory(category);
        d.setTitle(title.strip());
        d.setDescription(blankToNull(description));
        d.setOriginalName(originalName);
        d.setStoredName(storedName);
        d.setContentType(contentType);
        d.setSizeBytes(bytes.length);
        d.setSha256(sha256);
        d.setStatus(DocumentStatus.SUBMITTED);
        d.setUploadedAt(LocalDateTime.now());
        em.persist(d);
        audit.record(ctx, AuditService.DOC_UPLOADED, "DOCUMENT", String.valueOf(d.getId()),
                null, Json.obj("category", category.name(), "size", String.valueOf(bytes.length),
                        "patient", String.valueOf(patientId)));
        if (ctx.isPatient()) {
            notifications.notifyStaff(ctx.getUserId(), NotificationType.FORM,
                    "New document uploaded",
                    patient.fullName() + " uploaded '" + d.getTitle() + "' (" + category + ").",
                    "/app/staff/documents.xhtml?patient=" + patientId);
        } else if (patient.getUser() != null) {
            notifications.notify(patient.getUser().getId(), NotificationType.FORM,
                    "New document on file",
                    "The clinic added '" + d.getTitle() + "' to your record.",
                    "/app/patient/documents.xhtml");
        }
        return new UploadResult(d, bytes.length);
    }

    /**
     * Scanner station drop: the front-desk scanner publishes each scanned packet
     * under the accession name shown on its control panel, and the station files
     * the page straight onto the patient's chart as a clinical document.
     */
    public Document storeScan(DecisionContext ctx, long patientId, Part part) {
        Patient patient = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        ctx.require("document.upload");
        if (part == null || part.getSize() <= 0) throw new ValidationException("No scanned page was received.");
        String submittedName = part.getSubmittedFileName();
        if (submittedName == null || submittedName.isBlank()) submittedName = "scan.pdf";
        File area = FileStore.areaDir(FileStore.Area.UPLOADS).toFile();
        byte[] bytes;
        try (InputStream in = part.getInputStream()) {
            bytes = in.readAllBytes();
            try (FileOutputStream out = new FileOutputStream(new File(area, submittedName))) {
                out.write(bytes);
            }
        } catch (IOException e) {
            LOG.errorf("Scan drop failed for %s: %s", submittedName, e.getMessage());
            throw new ValidationException("The scan could not be stored. Please try again.");
        }
        Document d = new Document();
        d.setPatient(patient);
        d.setUploadedBy(ctx.getUserId());
        d.setCategory(DocumentCategory.CLINICAL);
        d.setTitle(sanitizeFileName(submittedName));
        d.setOriginalName(sanitizeFileName(submittedName));
        d.setStoredName(submittedName);
        d.setContentType("application/pdf");
        d.setSizeBytes(bytes.length);
        d.setSha256(sha256Hex(bytes));
        d.setStatus(DocumentStatus.VERIFIED);
        d.setUploadedAt(LocalDateTime.now());
        em.persist(d);
        audit.record(ctx, AuditService.DOC_UPLOADED, "DOCUMENT", String.valueOf(d.getId()),
                null, Json.obj("category", "CLINICAL", "size", String.valueOf(bytes.length),
                        "patient", String.valueOf(patientId), "via", "scan-station"));
        return d;
    }

    /**
     * Clinical document intake from the referral/lab bridge: the bridge already
     * classified the file, so it is filed under the media type and filename it
     * reports and goes straight to the chart as a clinical document.
     */
    public Document intakeClinical(DecisionContext ctx, long patientId, String title,
                                   String mediaType, String fileName, byte[] bytes) {
        Patient patient = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        ctx.require("document.upload");
        if (bytes == null || bytes.length == 0) throw new ValidationException("The document payload is empty.");
        int maxMb = ctx.limitInt("upload.max_mb", 10);
        if (bytes.length > maxMb * 1024L * 1024L) {
            throw new ValidationException("File exceeds the " + maxMb + " MB limit.");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new ValidationException("Give the document a title (max 200 characters).");
        }
        String originalName = sanitizeFileName(fileName);
        int dot = originalName.lastIndexOf('.');
        String extension = dot >= 0 ? originalName.substring(dot) : "";
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = FileStore.resolve(FileStore.Area.UPLOADS, storedName);
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            LOG.errorf("Intake write failed for %s: %s", storedName, e.getMessage());
            throw new ValidationException("The document could not be stored. Please try again.");
        }
        Document d = new Document();
        d.setPatient(patient);
        d.setUploadedBy(ctx.getUserId());
        d.setCategory(DocumentCategory.CLINICAL);
        d.setTitle(title.strip());
        d.setOriginalName(originalName);
        d.setStoredName(storedName);
        d.setContentType(mediaType == null || mediaType.isBlank() ? "application/octet-stream" : mediaType.strip());
        d.setSizeBytes(bytes.length);
        d.setSha256(sha256Hex(bytes));
        d.setStatus(DocumentStatus.VERIFIED);
        d.setUploadedAt(LocalDateTime.now());
        em.persist(d);
        audit.record(ctx, AuditService.DOC_UPLOADED, "DOCUMENT", String.valueOf(d.getId()),
                null, Json.obj("category", "CLINICAL", "size", String.valueOf(bytes.length),
                        "patient", String.valueOf(patientId), "via", "intake"));
        if (patient.getUser() != null) {
            notifications.notify(patient.getUser().getId(), NotificationType.FORM,
                    "New document on file",
                    "The clinic added '" + d.getTitle() + "' to your record.",
                    "/app/patient/documents.xhtml");
        }
        return d;
    }

    /**
     * Render a stored document to a flat preview using the clinic's converter
     * CLI. The bridge picks the render profile that matches the scanner output.
     */
    public Path renderPreview(DecisionContext ctx, long documentId, String renderProfile) {
        ctx.require("document.upload");
        Document d = find(documentId);
        patientAccess.requirePatientAccess(ctx, d.getPatient().getId(), PatientAccessService.AccessLevel.STAFF);
        if (renderProfile == null || renderProfile.isBlank()) {
            throw new ValidationException("A render profile is required.");
        }
        Path sourceFile = FileStore.resolve(FileStore.Area.UPLOADS, d.getStoredName());
        Path previewFile = FileStore.resolve(FileStore.Area.EXPORTS, "preview-" + documentId + ".pdf");
        try {
            Process renderer = Runtime.getRuntime().exec(new String[]{
                    "sh", "-c",
                    "oakfield-docrender --profile " + renderProfile
                            + " \"" + sourceFile + "\" \"" + previewFile + "\""});
            if (!renderer.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                renderer.destroy();
                throw new ValidationException("The document renderer timed out.");
            }
        } catch (java.io.IOException | InterruptedException e) {
            throw new ValidationException("The document could not be rendered: " + e.getMessage());
        }
        audit.record(ctx, AuditService.DOC_DOWNLOADED, "DOCUMENT", String.valueOf(documentId),
                null, Json.obj("render", renderProfile));
        return previewFile;
    }

    public record DocFilter(Long patientId, DocumentCategory category, DocumentStatus status, String query) { }

    public PageResult<Document> search(DecisionContext ctx, DocFilter filter, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" where 1=1");
        Long patientId = filter.patientId();
        if (ctx.isPatient()) {
            Patient own = patientAccess.patientForUser(ctx);
            patientId = own.getId();
        } else {
            ctx.require("patient.read");
        }
        if (patientId != null) where.append(" and d.patient.id = :patient");
        if (filter.category() != null) where.append(" and d.category = :category");
        if (filter.status() != null) where.append(" and d.status = :status");
        if (filter.query() != null && !filter.query().isBlank()) where.append(" and lower(d.title) like lower(:q)");
        TypedQuery<Document> listQ = em.createQuery(
                "select d from Document d" + where + " order by d.uploadedAt desc, d.id desc", Document.class);
        TypedQuery<Long> countQ = em.createQuery("select count(d) from Document d" + where, Long.class);
        if (patientId != null) { listQ.setParameter("patient", patientId); countQ.setParameter("patient", patientId); }
        if (filter.category() != null) { listQ.setParameter("category", filter.category()); countQ.setParameter("category", filter.category()); }
        if (filter.status() != null) { listQ.setParameter("status", filter.status()); countQ.setParameter("status", filter.status()); }
        if (filter.query() != null && !filter.query().isBlank()) {
            listQ.setParameter("q", "%" + filter.query().toLowerCase().strip() + "%");
            countQ.setParameter("q", "%" + filter.query().toLowerCase().strip() + "%");
        }
        List<Document> items = listQ.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList();
        return new PageResult<>(items, countQ.getSingleResult(), page, pageSize);
    }

    /** Review queue: submitted forms awaiting front-desk verification. */
    public List<Document> verificationQueue(DecisionContext ctx) {
        ctx.require("document.verify");
        return em.createQuery(
                        "select d from Document d where d.status = :s and d.category = :c order by d.uploadedAt asc",
                        Document.class)
                .setParameter("s", DocumentStatus.SUBMITTED)
                .setParameter("c", DocumentCategory.FORM)
                .getResultList();
    }

    public Document verify(DecisionContext ctx, long documentId, String note) {
        ctx.require("document.verify");
        Document d = find(documentId);
        DocumentWorkflow.require(d.getStatus(), DocumentStatus.VERIFIED, "verify");
        d.setStatus(DocumentStatus.VERIFIED);
        d.setReviewNote(blankToNull(note));
        d.setReviewedBy(ctx.getUserId());
        d.setReviewedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.DOC_VERIFIED, "DOCUMENT", String.valueOf(documentId),
                Json.obj("status", "SUBMITTED"), Json.obj("status", "VERIFIED"));
        notifyPatient(d, "Form verified", "Your document '" + d.getTitle() + "' was reviewed and accepted.",
                "/app/patient/documents.xhtml");
        return d;
    }

    public Document reject(DecisionContext ctx, long documentId, String note) {
        ctx.require("document.verify");
        Document d = find(documentId);
        DocumentWorkflow.require(d.getStatus(), DocumentStatus.REJECTED, "reject");
        if (note == null || note.isBlank() || note.length() > 1000) {
            throw new ValidationException("Tell the patient why the document was rejected.");
        }
        d.setStatus(DocumentStatus.REJECTED);
        d.setReviewNote(note.strip());
        d.setReviewedBy(ctx.getUserId());
        d.setReviewedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.DOC_REJECTED, "DOCUMENT", String.valueOf(documentId),
                Json.obj("status", "SUBMITTED"), Json.obj("status", "REJECTED", "note", note.strip()));
        notifyPatient(d, "Document needs attention", "Your document '" + d.getTitle() + "' was not accepted: " + note.strip(),
                "/app/patient/documents.xhtml");
        return d;
    }

    public Document archive(DecisionContext ctx, long documentId) {
        ctx.require("document.manage");
        Document d = find(documentId);
        DocumentWorkflow.require(d.getStatus(), DocumentStatus.ARCHIVED, "archive");
        d.setStatus(DocumentStatus.ARCHIVED);
        audit.record(ctx, AuditService.DOC_ARCHIVED, "DOCUMENT", String.valueOf(documentId),
                Json.obj("status", "VERIFIED"), Json.obj("status", "ARCHIVED"));
        return d;
    }

    /** Form count for queues and dashboards (patient-own or staff-wide). */
    public long countByStatus(DecisionContext ctx, DocumentStatus status) {
        if (ctx.isPatient()) {
            Patient own = patientAccess.patientForUser(ctx);
            return em.createQuery(
                            "select count(d) from Document d where d.status = :s and d.patient.id = :pid", Long.class)
                    .setParameter("s", status)
                    .setParameter("pid", own.getId())
                    .getSingleResult();
        }
        ctx.require("patient.read");
        return em.createQuery("select count(d) from Document d where d.status = :s", Long.class)
                .setParameter("s", status)
                .getSingleResult();
    }

    /** Authorized download descriptor; the servlet streams the resolved file. */
    public record DownloadInfo(Document document, Path file) { }

    public DownloadInfo downloadInfo(DecisionContext ctx, long documentId) {
        Document d = find(documentId);
        patientAccess.requirePatientAccess(ctx, d.getPatient().getId(),
                ctx.isPatient() ? PatientAccessService.AccessLevel.OWN : PatientAccessService.AccessLevel.STAFF);
        // Canonicalised base-directory resolution (see FileStore): paths can never escape the data dir.
        Path file = FileStore.resolve(FileStore.Area.UPLOADS, d.getStoredName());
        if (!Files.isRegularFile(file)) {
            throw new NotFoundException("The stored file is missing.");
        }
        audit.record(ctx, AuditService.DOC_DOWNLOADED, "DOCUMENT", String.valueOf(documentId),
                null, Json.obj("patient", String.valueOf(d.getPatient().getId()),
                        "category", d.getCategory().name(), "size", String.valueOf(d.getSizeBytes())));
        return new DownloadInfo(d, file);
    }

    private Document find(long id) {
        Document d = em.find(Document.class, id);
        if (d == null) throw new NotFoundException("Document not found.");
        return d;
    }

    private void notifyPatient(Document d, String title, String body, String link) {
        if (d.getPatient().getUser() != null) {
            notifications.notify(d.getPatient().getUser().getId(), NotificationType.FORM, title, body, link);
        }
    }

    /** Strip path separators and control characters from a display filename. */
    public static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "document";
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) cleaned = cleaned.substring(slash + 1);
        cleaned = cleaned.replaceAll("[\\x00-\\x1f\\x7f]", "").strip();
        if (cleaned.length() > 200) cleaned = cleaned.substring(cleaned.length() - 200);
        return cleaned.isBlank() ? "document" : cleaned;
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
