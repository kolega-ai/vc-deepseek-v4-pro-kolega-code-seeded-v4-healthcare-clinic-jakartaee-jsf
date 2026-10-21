package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.ExportJob;
import com.oakfield.clinic.svc.ImportExportService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;

/**
 * CSV schedule export for integrations. Requires an API key with scope
 * export.read or a session with export.schedule. Every run is audited.
 */
@Path("/v1/exports")
public class ExportApiResource {

    @Inject
    private ImportExportService importExport;

    @GET
    @Path("/schedule")
    public Response schedule(@QueryParam("from") String from,
                             @QueryParam("to") String to,
                             @QueryParam("providerId") Long providerId,
                             @QueryParam("status") String status,
                             @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        if (!ctx.hasScope("export.read") && !ctx.hasScope("export.schedule")) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Scope export.read or export.schedule is required.");
        }
        LocalDate fromDate, toDate;
        try {
            fromDate = LocalDate.parse(from);
            toDate = LocalDate.parse(to);
        } catch (Exception e) {
            throw new ValidationException("from and to must be yyyy-MM-dd.");
        }
        if (toDate.isBefore(fromDate) || toDate.isAfter(fromDate.plusDays(93))) {
            throw new ValidationException("Date range must be within 93 days.");
        }
        AppointmentStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = AppointmentStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Unknown appointment status filter.");
            }
        }
        ExportJob job = importExport.exportSchedule(ctx,
                new ImportExportService.ExportFilter(fromDate, toDate, providerId, statusFilter));
        ImportExportService.ExportDownload download = importExport.exportDownload(ctx, job.getId());
        try {
            byte[] bytes = Files.readAllBytes(download.file());
            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=\"" + job.getFileName() + "\"")
                    .header("Content-Type", "text/csv; charset=utf-8")
                    .build();
        } catch (IOException e) {
            throw new ValidationException("Export file could not be read.");
        }
    }

    /**
     * Manifest drop used by the clearing house: it posts the monthly manifest it
     * produced (base64), named the way its own tooling names files, and the
     * clinic files it into the export store for the billing run.
     */
    @POST
    @Path("/manifest")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response manifest(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        if (!ctx.hasScope("export.read") && !ctx.hasScope("export.schedule")) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Scope export.read or export.schedule is required.");
        }
        String fn;
        byte[] bs;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            fn = o.getString("fileName", null);
            bs = Base64.getDecoder().decode(o.getString("content"));
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with fileName and content (base64).");
        }
        ExportJob job = importExport.stageManifest(ctx, fn, bs);
        return Response.status(Response.Status.CREATED)
                .entity("{\"exportJobId\":" + job.getId() + ",\"fileName\":\"" + JsonEsc.escape(job.getFileName()) + "\"}")
                .build();
    }
}
