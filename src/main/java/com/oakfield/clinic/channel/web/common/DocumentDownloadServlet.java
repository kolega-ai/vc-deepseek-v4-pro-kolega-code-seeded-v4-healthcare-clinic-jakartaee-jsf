package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.svc.DocumentService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Authorized document download. Ownership/scope is re-verified by the
 * decision tier inside {@link DocumentService#downloadInfo} (patients: own
 * records only; staff: patient.read), every download is audited, and the file
 * path is resolved through the canonicalised base directory in FileStore.
 */
@WebServlet("/app/files/*")
public class DocumentDownloadServlet extends HttpServlet {

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
        String pathInfo = request.getPathInfo();
        long documentId;
        try {
            documentId = Long.parseLong(pathInfo.substring(1));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        DocumentService service = CDI.current().select(DocumentService.class).get();
        DocumentService.DownloadInfo info;
        try {
            info = service.downloadInfo(ctx, documentId);
        } catch (com.oakfield.clinic.decision.AccessDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Document d = info.document();
        Path file = info.file();
        response.setContentType(d.getContentType());
        response.setContentLengthLong(Files.size(file));
        response.setHeader("X-Content-Type-Options", "nosniff");
        String encoded = URLEncoder.encode(d.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + d.getOriginalName() + "\"; filename*=UTF-8''" + encoded);
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(file, out);
        }
    }
}
