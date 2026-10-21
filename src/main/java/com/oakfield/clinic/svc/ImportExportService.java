package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CSV import for patient records and CSV exports for appointment schedules,
 * visit summaries (sensitive), and the audit log. All rows are parsed with a
 * strict header, validated server-side, and written through the FileStore
 * canonical base directory. Every run is audited.
 */
@Stateless
public class ImportExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String[] PATIENT_HEADER = {
            "firstName", "lastName", "dateOfBirth", "gender", "email", "phone",
            "addressLine1", "city", "state", "postalCode",
            "insuranceProvider", "insurancePolicyNumber", "insuranceGroupNumber",
            "emergencyContactName", "emergencyContactPhone", "primaryProviderNPI"};

    @PersistenceContext
    private EntityManager em;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    @EJB
    private ProviderService providers;

    // ------------------------------------------------------------ import

    public ImportJob importPatients(DecisionContext ctx, String originalName, InputStream data) {
        ctx.require("import.patients");
        int maxRows = ctx.limitInt("import.max_rows", 5000);
        ImportJob job = new ImportJob();
        job.setKind("PATIENTS");
        job.setOriginalName(DocumentService.sanitizeFileName(originalName));
        job.setStatus(JobStatus.RUNNING);
        job.setCreatedBy(ctx.getUserId());
        em.persist(job);
        audit.record(ctx, AuditService.IMPORT_STARTED, "IMPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("file", job.getOriginalName()));

        List<String[]> errors = new ArrayList<>();
        int total = 0, success = 0;
        try (Reader reader = new InputStreamReader(data, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(PATIENT_HEADER).setSkipHeaderRecord(true).build();
            Iterable<CSVRecord> records = format.parse(reader);
            for (CSVRecord r : records) {
                total++;
                if (total > maxRows) {
                    errors.add(new String[]{String.valueOf(total), "Import exceeds the " + maxRows + " row limit."});
                    continue;
                }
                List<String> rowErrors = new ArrayList<>();
                Patient p = parseRow(r, rowErrors);
                if (rowErrors.isEmpty()) {
                    em.persist(p);
                    success++;
                    audit.record(ctx, AuditService.PATIENT_IMPORTED, "PATIENT", String.valueOf(p.getId()),
                            null, Json.obj("name", p.fullName(), "importJob", String.valueOf(job.getId())));
                } else {
                    errors.add(new String[]{String.valueOf(total), String.join("; ", rowErrors)});
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            job.setStatus(JobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            throw new ValidationException("Could not read the CSV file: " + e.getMessage());
        }

        job.setTotalRows(total);
        job.setSuccessRows(success);
        job.setErrorRows(errors.size());
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        if (!errors.isEmpty()) {
            String reportName = "import-errors-" + job.getId() + ".csv";
            Path report = FileStore.resolve(FileStore.Area.IMPORTS, reportName);
            try (Writer writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8);
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                         .setHeader("rowNumber", "errors").build())) {
                for (String[] err : errors) printer.printRecord((Object[]) err);
            } catch (IOException e) {
                throw new ValidationException("Could not write the error report: " + e.getMessage());
            }
            job.setErrorReportName(reportName);
        }
        audit.record(ctx, AuditService.IMPORT_COMPLETED, "IMPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("total", String.valueOf(total), "success", String.valueOf(success),
                        "errors", String.valueOf(errors.size())));
        if (ctx.getUserId() != null) {
            notifications.notify(ctx.getUserId(), NotificationType.SYSTEM,
                    "Patient import finished",
                    job.getOriginalName() + ": " + success + " imported, " + errors.size() + " errors.",
                    "/app/admin/imports.xhtml");
        }
        return job;
    }

    private Patient parseRow(CSVRecord r, List<String> errors) {
        Patient p = new Patient();
        p.setFirstName(val(r, "firstName"));
        p.setLastName(val(r, "lastName"));
        if (p.getFirstName() == null || p.getFirstName().isBlank() || p.getFirstName().length() > 120) {
            errors.add("firstName required (max 120)");
        }
        if (p.getLastName() == null || p.getLastName().isBlank() || p.getLastName().length() > 120) {
            errors.add("lastName required (max 120)");
        }
        String dob = val(r, "dateOfBirth");
        if (dob == null) {
            errors.add("dateOfBirth required (yyyy-MM-dd)");
        } else {
            try {
                p.setDateOfBirth(LocalDate.parse(dob, DATE));
                if (p.getDateOfBirth().isAfter(LocalDate.now())) errors.add("dateOfBirth in the future");
                if (p.getDateOfBirth().isBefore(LocalDate.now().minusYears(120))) errors.add("dateOfBirth not plausible");
            } catch (Exception e) {
                errors.add("dateOfBirth must be yyyy-MM-dd");
            }
        }
        p.setGender(PatientService.parseGender(val(r, "gender")));
        String email = val(r, "email");
        if (email != null && !email.isBlank() && (email.length() > 255 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            errors.add("email invalid");
        }
        p.setEmail(PatientService.blankToNull(email));
        p.setPhone(PatientService.blankToNull(val(r, "phone")));
        p.setAddressLine1(PatientService.blankToNull(val(r, "addressLine1")));
        p.setCity(PatientService.blankToNull(val(r, "city")));
        p.setState(PatientService.blankToNull(val(r, "state")));
        p.setPostalCode(PatientService.blankToNull(val(r, "postalCode")));
        p.setInsuranceProvider(PatientService.blankToNull(val(r, "insuranceProvider")));
        p.setInsurancePolicyNumber(PatientService.blankToNull(val(r, "insurancePolicyNumber")));
        p.setInsuranceGroupNumber(PatientService.blankToNull(val(r, "insuranceGroupNumber")));
        p.setEmergencyContactName(PatientService.blankToNull(val(r, "emergencyContactName")));
        p.setEmergencyContactPhone(PatientService.blankToNull(val(r, "emergencyContactPhone")));
        String npi = val(r, "primaryProviderNPI");
        if (npi != null && !npi.isBlank()) {
            Provider provider = providers.byNpi(npi.strip());
            if (provider == null) errors.add("primaryProviderNPI not found: " + npi);
            else p.setPrimaryProvider(provider);
        }
        p.setActive(true);
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    private static String val(CSVRecord r, String column) {
        try {
            return r.get(column);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ exports

    public record ExportFilter(LocalDate from, LocalDate to, Long providerId, AppointmentStatus status) { }

    public ExportJob exportSchedule(DecisionContext ctx, ExportFilter filter) {
        ctx.require("export.schedule");
        int maxRows = ctx.limitInt("export.max_rows", 5000);
        StringBuilder jpql = new StringBuilder("select a from Appointment a where a.startTime >= :from and a.startTime < :to");
        if (filter.providerId() != null) jpql.append(" and a.provider.id = :provider");
        if (filter.status() != null) jpql.append(" and a.status = :status");
        jpql.append(" order by a.startTime asc");
        var q = em.createQuery(jpql.toString(), Appointment.class)
                .setParameter("from", filter.from().atStartOfDay())
                .setParameter("to", filter.to().plusDays(1).atStartOfDay());
        if (filter.providerId() != null) q.setParameter("provider", filter.providerId());
        if (filter.status() != null) q.setParameter("status", filter.status());
        List<Appointment> rows = q.setMaxResults(maxRows).getResultList();

        ExportJob job = new ExportJob();
        job.setKind(ExportKind.SCHEDULE);
        job.setParams(Json.obj("from", String.valueOf(filter.from()), "to", String.valueOf(filter.to()),
                "provider", filter.providerId() == null ? "" : String.valueOf(filter.providerId()),
                "status", filter.status() == null ? "" : filter.status().name()));
        job.setCreatedBy(ctx.getUserId());
        job.setStatus(JobStatus.COMPLETED);
        job.setFileName("schedule-" + LocalDate.now() + "-" + jobSuffix() + ".csv");
        Path file = FileStore.resolve(FileStore.Area.EXPORTS, job.getFileName());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("start", "end", "provider", "patient", "patientDob", "type", "status", "reason", "notes").build())) {
            for (Appointment a : rows) {
                printer.printRecord(
                        a.getStartTime().format(TIME),
                        a.getEndTime().format(TIME),
                        a.getProvider().fullName(),
                        a.getPatient().fullName(),
                        a.getPatient().getDateOfBirth() == null ? "" : a.getPatient().getDateOfBirth().format(DATE),
                        a.getType().getName(),
                        a.getStatus().name(),
                        a.getReason() == null ? "" : a.getReason(),
                        a.getNotes() == null ? "" : a.getNotes());
            }
        } catch (IOException e) {
            throw new ValidationException("Could not write the export file: " + e.getMessage());
        }
        em.persist(job);
        audit.record(ctx, AuditService.EXPORT_RUN, "EXPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("kind", "SCHEDULE", "rows", String.valueOf(rows.size())));
        return job;
    }

    /** Sensitive clinical export: visit summaries (clinicians and admins only). */
    public ExportJob exportSummaries(DecisionContext ctx, LocalDate from, LocalDate to, Long providerId) {
        ctx.require("export.clinical");
        int maxRows = ctx.limitInt("export.max_rows", 5000);
        StringBuilder jpql = new StringBuilder(
                "select v from VisitSummary v where v.appointment.startTime >= :from and v.appointment.startTime < :to");
        if (providerId != null) jpql.append(" and v.provider.id = :provider");
        jpql.append(" order by v.appointment.startTime asc");
        var q = em.createQuery(jpql.toString(), VisitSummary.class)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay());
        if (providerId != null) q.setParameter("provider", providerId);
        List<VisitSummary> rows = q.setMaxResults(maxRows).getResultList();

        ExportJob job = new ExportJob();
        job.setKind(ExportKind.VISIT_SUMMARIES);
        job.setParams(Json.obj("from", String.valueOf(from), "to", String.valueOf(to),
                "provider", providerId == null ? "" : String.valueOf(providerId)));
        job.setCreatedBy(ctx.getUserId());
        job.setStatus(JobStatus.COMPLETED);
        job.setFileName("visit-summaries-" + LocalDate.now() + "-" + jobSuffix() + ".csv");
        Path file = FileStore.resolve(FileStore.Area.EXPORTS, job.getFileName());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("visitDate", "patient", "patientDob", "provider", "status", "diagnosis",
                             "careInstructions", "followUpPlan").build())) {
            for (VisitSummary v : rows) {
                printer.printRecord(
                        v.getAppointment().getStartTime().toLocalDate().format(DATE),
                        v.getPatient().fullName(),
                        v.getPatient().getDateOfBirth() == null ? "" : v.getPatient().getDateOfBirth().format(DATE),
                        v.getProvider().fullName(),
                        v.getStatus().name(),
                        v.getDiagnosisText() == null ? "" : v.getDiagnosisText(),
                        v.getCareInstructions() == null ? "" : v.getCareInstructions(),
                        v.getFollowUpPlan() == null ? "" : v.getFollowUpPlan());
            }
        } catch (IOException e) {
            throw new ValidationException("Could not write the export file: " + e.getMessage());
        }
        em.persist(job);
        audit.record(ctx, AuditService.EXPORT_RUN, "EXPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("kind", "VISIT_SUMMARIES", "rows", String.valueOf(rows.size())));
        return job;
    }

    public ExportJob exportAudit(DecisionContext ctx, LocalDate from, LocalDate to, String action) {
        ctx.require("export.audit");
        int maxRows = ctx.limitInt("export.max_rows", 20000);
        var filter = new AuditService.AuditFilter(from, to, action, null, null);
        List<AuditEvent> rows = audit.search(filter, 0, maxRows);
        ExportJob job = new ExportJob();
        job.setKind(ExportKind.AUDIT);
        job.setParams(Json.obj("from", String.valueOf(from), "to", String.valueOf(to),
                "action", action == null ? "" : action));
        job.setCreatedBy(ctx.getUserId());
        job.setStatus(JobStatus.COMPLETED);
        job.setFileName("audit-" + LocalDate.now() + "-" + jobSuffix() + ".csv");
        Path file = FileStore.resolve(FileStore.Area.EXPORTS, job.getFileName());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("occurredAt", "actor", "actorRole", "action", "targetType", "targetId",
                             "before", "after", "authzBasis", "ip").build())) {
            for (AuditEvent a : rows) {
                printer.printRecord(
                        a.getOccurredAt().format(TIME),
                        a.getActorName() == null ? "" : a.getActorName(),
                        a.getActorRole() == null ? "" : a.getActorRole(),
                        a.getAction(),
                        a.getTargetType() == null ? "" : a.getTargetType(),
                        a.getTargetId() == null ? "" : a.getTargetId(),
                        a.getBeforeValue() == null ? "" : a.getBeforeValue(),
                        a.getAfterValue() == null ? "" : a.getAfterValue(),
                        a.getAuthzBasis() == null ? "" : a.getAuthzBasis(),
                        a.getIpAddress() == null ? "" : a.getIpAddress());
            }
        } catch (IOException e) {
            throw new ValidationException("Could not write the export file: " + e.getMessage());
        }
        em.persist(job);
        audit.record(ctx, AuditService.EXPORT_RUN, "EXPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("kind", "AUDIT", "rows", String.valueOf(rows.size())));
        return job;
    }

    public List<ExportJob> recentExports(DecisionContext ctx) {
        boolean admin = ctx.hasScope("export.audit");
        return em.createQuery("select e from ExportJob e order by e.createdAt desc, e.id desc", ExportJob.class)
                .setMaxResults(50)
                .getResultList().stream()
                .filter(e -> admin || e.getKind() != ExportKind.AUDIT)
                .toList();
    }

    /** Download an export file (scope re-checked by kind; file resolved through the canonical base dir). */
    public record ExportDownload(ExportJob job, Path file) { }

    public ExportDownload exportDownload(DecisionContext ctx, long exportJobId) {
        ExportJob job = em.find(ExportJob.class, exportJobId);
        if (job == null) throw new NotFoundException("Export not found.");
        switch (job.getKind()) {
            case SCHEDULE -> ctx.require("export.schedule");
            case VISIT_SUMMARIES -> ctx.require("export.clinical");
            case AUDIT -> ctx.require("export.audit");
        }
        Path file = FileStore.resolve(FileStore.Area.EXPORTS, job.getFileName());
        if (!Files.isRegularFile(file)) throw new NotFoundException("Export file missing.");
        return new ExportDownload(job, file);
    }

    public ImportJob latestImportJob(DecisionContext ctx) {
        return em.createQuery("select i from ImportJob i order by i.createdAt desc", ImportJob.class)
                .setMaxResults(1)
                .getResultStream().findFirst().orElse(null);
    }

    public List<ImportJob> recentImports(int limit) {
        return em.createQuery("select i from ImportJob i order by i.createdAt desc, i.id desc", ImportJob.class)
                .setMaxResults(limit)
                .getResultList();
    }

    /** Download the row-level error report of an import. */
    public record ErrorReportDownload(ImportJob job, Path file) { }

    public ErrorReportDownload errorReportDownload(DecisionContext ctx, long importJobId) {
        ctx.require("import.patients");
        ImportJob job = em.find(ImportJob.class, importJobId);
        if (job == null || job.getErrorReportName() == null) throw new NotFoundException("No error report for this import.");
        Path file = FileStore.resolve(FileStore.Area.IMPORTS, job.getErrorReportName());
        if (!Files.isRegularFile(file)) throw new NotFoundException("Error report file missing.");
        return new ErrorReportDownload(job, file);
    }

    public static String jobSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Stage a partner-supplied manifest into the export store. The clearing house
     * produces these files itself and names them with its own convention, so the
     * name it sends is kept; the file then shows up in the export history.
     */
    public ExportJob stageManifest(DecisionContext ctx, String fileName, byte[] content) {
        ctx.require("export.schedule");
        if (fileName == null || fileName.isBlank()) throw new ValidationException("The manifest needs a file name.");
        if (content == null || content.length == 0) throw new ValidationException("The manifest is empty.");
        Path target = FileStore.areaDir(FileStore.Area.EXPORTS).resolve(fileName);
        try (OutputStream out = Files.newOutputStream(target,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(content);
        } catch (IOException e) {
            throw new ValidationException("The manifest could not be stored: " + e.getMessage());
        }
        ExportJob job = new ExportJob();
        job.setKind(ExportKind.SCHEDULE);
        job.setParams(Json.obj("via", "manifest", "name", fileName));
        job.setCreatedBy(ctx.getUserId());
        job.setStatus(JobStatus.COMPLETED);
        job.setFileName(fileName);
        em.persist(job);
        audit.record(ctx, AuditService.EXPORT_RUN, "EXPORT_JOB", String.valueOf(job.getId()),
                null, Json.obj("kind", "MANIFEST", "name", fileName));
        return job;
    }
}
