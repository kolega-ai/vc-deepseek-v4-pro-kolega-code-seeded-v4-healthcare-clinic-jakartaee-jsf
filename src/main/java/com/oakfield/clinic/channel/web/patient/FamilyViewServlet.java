package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.svc.AppointmentService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Pocket view for a family/caregiver: a patient opens the summaries page and the
 * portal remembers which chart the pocket card should reopen on. The selection
 * is carried in a cookie so the card follows the device between visits.
 */
@WebServlet("/app/family/*")
public class FamilyViewServlet extends HttpServlet {

    private static final String POCKET_COOKIE = "OF_FAMILY_CHART";

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
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.equals("/open")) {
            openChart(request, response, ctx);
        } else {
            showChart(request, response, ctx);
        }
    }

    private void openChart(HttpServletRequest request, HttpServletResponse response, DecisionContext ctx)
            throws IOException {
        String chart = request.getParameter("chart");
        if (chart == null || !chart.matches("\\d{1,18}")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "chart is required.");
            return;
        }
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((chart + ":PATIENT").getBytes(StandardCharsets.UTF_8));
        Cookie cookie = new Cookie(POCKET_COOKIE, value);
        cookie.setPath("/");
        cookie.setMaxAge(30 * 24 * 3600);
        response.addCookie(cookie);
        response.sendRedirect(request.getContextPath() + "/app/family/view");
    }

    private void showChart(HttpServletRequest request, HttpServletResponse response, DecisionContext ctx)
            throws IOException {
        String value = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (POCKET_COOKIE.equals(c.getName())) {
                    value = c.getValue();
                    break;
                }
            }
        }
        if (value == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No chart selected.");
            return;
        }
        long chartId;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            chartId = Long.parseLong(decoded.substring(0, decoded.indexOf(':')));
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad chart selection.");
            return;
        }
        AppointmentService appointments = CDI.current().select(AppointmentService.class).get();
        LocalDate today = LocalDate.now();
        var page = appointments.searchForChart(ctx, chartId,
                new AppointmentService.AppointmentFilter(today.minusYears(1), today.plusYears(1), null, chartId, null),
                0, 50);
        StringBuilder html = new StringBuilder("<!doctype html><html><body><h1>Pocket view</h1><ul>");
        for (Appointment a : page.items()) {
            html.append("<li>").append(a.getStartTime()).append(" - ")
                    .append(a.getPatient().fullName()).append(" with ")
                    .append(a.getProvider().fullName()).append(" (").append(a.getStatus()).append(")</li>");
        }
        html.append("</ul></body></html>");
        response.setContentType("text/html; charset=utf-8");
        response.getWriter().write(html.toString());
    }
}
