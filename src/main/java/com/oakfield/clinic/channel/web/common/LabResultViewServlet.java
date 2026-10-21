package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.svc.AuditService;
import com.oakfield.clinic.svc.FileStore;
import com.oakfield.clinic.svc.Json;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Support-desk lab report viewer. The report the desk was last reviewing is kept
 * on the device so the viewer reopens on the same file between calls.
 */
@WebServlet("/app/lab-views/*")
public class LabResultViewServlet extends HttpServlet {

    private static final String VIEW_COOKIE = "OF_LAB_VIEW";

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
        ctx.require("patient.read");
        String reportName = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (VIEW_COOKIE.equals(c.getName())) {
                    reportName = c.getValue();
                    break;
                }
            }
        }
        if (reportName == null || reportName.isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No report selected.");
            return;
        }
        Path report = FileStore.areaDir(FileStore.Area.UPLOADS).resolve(reportName);
        if (!Files.isRegularFile(report)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Report is missing.");
            return;
        }
        CDI.current().select(AuditService.class).get().record(ctx, AuditService.DOC_DOWNLOADED, "DOCUMENT",
                reportName, null, Json.obj("via", "lab-viewer", "file", reportName));
        response.setContentType("application/pdf");
        response.setContentLengthLong(Files.size(report));
        response.setHeader("X-Content-Type-Options", "nosniff");
        try (InputStream in = Files.newInputStream(report);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
