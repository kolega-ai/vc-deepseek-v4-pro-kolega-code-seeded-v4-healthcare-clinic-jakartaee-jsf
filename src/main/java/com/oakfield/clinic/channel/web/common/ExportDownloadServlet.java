package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.svc.ImportExportService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Authorized export download (schedule CSV, visit summaries CSV, audit CSV).
 * The scope for the export kind is re-checked server-side on every download
 * and the file is resolved through the canonicalised base directory.
 */
@WebServlet("/app/exports/*")
public class ExportDownloadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        DecisionContext ctx;
        try {
            ctx = DecisionContextFilter.ctxOf(request);
        } catch (NotAuthenticatedException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        long exportId;
        try {
            exportId = Long.parseLong(request.getPathInfo().substring(1));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ImportExportService service = CDI.current().select(ImportExportService.class).get();
        ImportExportService.ExportDownload download;
        try {
            download = service.exportDownload(ctx, exportId);
        } catch (com.oakfield.clinic.decision.AccessDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("text/csv; charset=utf-8");
        response.setContentLengthLong(Files.size(download.file()));
        response.setHeader("Content-Disposition", "attachment; filename=\"" + download.job().getFileName() + "\"");
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(download.file(), out);
        }
    }
}
