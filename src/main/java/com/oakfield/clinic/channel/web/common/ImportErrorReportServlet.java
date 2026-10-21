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

/** Authorized download of an import's row-level error report (import.patients scope). */
@WebServlet("/app/imports/errors/*")
public class ImportErrorReportServlet extends HttpServlet {

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
        long importId;
        try {
            importId = Long.parseLong(request.getPathInfo().substring(1));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        ImportExportService service = CDI.current().select(ImportExportService.class).get();
        ImportExportService.ErrorReportDownload download;
        try {
            download = service.errorReportDownload(ctx, importId);
        } catch (com.oakfield.clinic.decision.AccessDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("text/csv; charset=utf-8");
        response.setContentLengthLong(Files.size(download.file()));
        response.setHeader("Content-Disposition", "attachment; filename=\"import-errors-" + importId + ".csv\"");
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(download.file(), out);
        }
    }
}
