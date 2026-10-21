package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * View formatting helpers: status badges, labels, dates, and money rendering.
 * Badges pair a color with a visible text label (never color-only).
 */
@Named("ux")
@ApplicationScoped
public class Ux implements Serializable {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    public String appointmentBadge(AppointmentStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case REQUESTED -> "badge badge--blue";
            case SCHEDULED -> "badge badge--teal";
            case CHECKED_IN -> "badge badge--violet";
            case COMPLETED -> "badge badge--green";
            case CANCELLED -> "badge badge--gray";
            case NO_SHOW -> "badge badge--red";
        };
    }

    public String appointmentLabel(AppointmentStatus s) {
        if (s == null) return "Unknown";
        return switch (s) {
            case REQUESTED -> "Requested";
            case SCHEDULED -> "Scheduled";
            case CHECKED_IN -> "Checked in";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
            case NO_SHOW -> "No show";
        };
    }

    public String refillBadge(RefillStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case NONE -> "badge badge--gray";
            case REQUESTED -> "badge badge--blue";
            case NEEDS_INFO -> "badge badge--amber";
            case APPROVED -> "badge badge--green";
            case DENIED -> "badge badge--red";
            case CANCELLED -> "badge badge--gray";
        };
    }

    public String refillLabel(RefillStatus s) {
        if (s == null) return "No refill";
        return switch (s) {
            case NONE -> "No refill";
            case REQUESTED -> "Refill requested";
            case NEEDS_INFO -> "Needs info";
            case APPROVED -> "Approved";
            case DENIED -> "Denied";
            case CANCELLED -> "Cancelled";
        };
    }

    public String docBadge(DocumentStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case SUBMITTED -> "badge badge--blue";
            case VERIFIED -> "badge badge--green";
            case REJECTED -> "badge badge--red";
            case ARCHIVED -> "badge badge--gray";
        };
    }

    public String docLabel(DocumentStatus s) {
        if (s == null) return "Unknown";
        return switch (s) {
            case SUBMITTED -> "Submitted";
            case VERIFIED -> "Verified";
            case REJECTED -> "Rejected";
            case ARCHIVED -> "Archived";
        };
    }

    public String visitBadge(VisitStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case DRAFT -> "badge badge--amber";
            case RELEASED -> "badge badge--green";
        };
    }

    public String userStatusBadge(UserStatus s) {
        return s == UserStatus.ACTIVE ? "badge badge--green" : "badge badge--gray";
    }

    public String jobBadge(JobStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case PENDING -> "badge badge--blue";
            case RUNNING -> "badge badge--amber";
            case COMPLETED -> "badge badge--green";
            case FAILED -> "badge badge--red";
        };
    }

    public String deliveryBadge(DeliveryStatus s) {
        if (s == null) return "badge badge--gray";
        return switch (s) {
            case PENDING -> "badge badge--blue";
            case SENT -> "badge badge--green";
            case FAILED -> "badge badge--red";
        };
    }

    public String roleLabel(String role) {
        if (role == null) return "Unknown";
        return switch (role) {
            case "CLINIC_ADMIN" -> "Clinic Admin";
            case "CLINICIAN" -> "Clinician";
            case "FRONT_DESK" -> "Front Desk";
            case "PATIENT" -> "Patient";
            case "API_KEY" -> "API Key";
            case "SYSTEM" -> "System Job";
            case "ANONYMOUS" -> "Anonymous";
            default -> role;
        };
    }

    public String genderLabel(Gender g) {
        if (g == null) return "Not stated";
        return switch (g) {
            case F -> "Female";
            case M -> "Male";
            case X -> "Non-binary";
            case U -> "Not stated";
        };
    }

    public String fmtDate(LocalDate d) { return d == null ? "—" : d.format(DATE); }

    public String fmtDateIso(LocalDate d) { return d == null ? "" : d.toString(); }

    public String fmtTime(LocalDateTime t) { return t == null ? "—" : t.format(TIME); }

    public String fmtDateTime(LocalDateTime t) { return t == null ? "—" : t.format(DATETIME); }

    public String money(BigDecimal amount, String currency) {
        if (amount == null) return "—";
        String symbol = "USD".equals(currency) ? "$" : currency + " ";
        return symbol + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public String fileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public String docIcon(DocumentCategory c) {
        return c == DocumentCategory.FORM ? "F" : "C";
    }

    public String truncated(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
