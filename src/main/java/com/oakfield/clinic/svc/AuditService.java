package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.AuditEvent;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Append-only audit log. Only inserts are exposed; there is no update or
 * delete path in the application, so no role can modify or remove an audit
 * record through any screen, endpoint, or export.
 *
 * Every state transition and privileged action records: actor, action,
 * before/after values, timestamp, and the authorization basis.
 * Read-auditing (e.g. patient chart reads) records ONE event per request
 * naming the resource class and the count returned - never one event per record.
 */
@Stateless
public class AuditService {

    /** Audit action constants. */
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTRATION = "REGISTRATION";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String INVITE_CREATED = "INVITE_CREATED";
    public static final String INVITE_ACCEPTED = "INVITE_ACCEPTED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_ENABLED = "USER_ENABLED";
    public static final String PATIENT_CREATED = "PATIENT_CREATED";
    public static final String PATIENT_UPDATED = "PATIENT_UPDATED";
    public static final String PATIENT_IMPORTED = "PATIENT_IMPORTED";
    public static final String APPOINTMENT_BOOKED = "APPOINTMENT_BOOKED";
    public static final String APPOINTMENT_CONFIRMED = "APPOINTMENT_CONFIRMED";
    public static final String APPOINTMENT_CHECKED_IN = "APPOINTMENT_CHECKED_IN";
    public static final String APPOINTMENT_COMPLETED = "APPOINTMENT_COMPLETED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String APPOINTMENT_NO_SHOW = "APPOINTMENT_NO_SHOW";
    public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";
    public static final String VISIT_CREATED = "VISIT_CREATED";
    public static final String VISIT_EDITED = "VISIT_EDITED";
    public static final String VISIT_RELEASED = "VISIT_RELEASED";
    public static final String RX_CREATED = "RX_CREATED";
    public static final String RX_DISCONTINUED = "RX_DISCONTINUED";
    public static final String REFILL_REQUESTED = "REFILL_REQUESTED";
    public static final String REFILL_APPROVED = "REFILL_APPROVED";
    public static final String REFILL_DENIED = "REFILL_DENIED";
    public static final String REFILL_NEEDS_INFO = "REFILL_NEEDS_INFO";
    public static final String REFILL_CANCELLED = "REFILL_CANCELLED";
    public static final String DOC_UPLOADED = "DOC_UPLOADED";
    public static final String DOC_VERIFIED = "DOC_VERIFIED";
    public static final String DOC_REJECTED = "DOC_REJECTED";
    public static final String DOC_ARCHIVED = "DOC_ARCHIVED";
    public static final String DOC_DOWNLOADED = "DOC_DOWNLOADED";
    public static final String CHART_VIEW = "CHART_VIEW";
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String NOTE_ADDED = "NOTE_ADDED";
    public static final String IMPORT_STARTED = "IMPORT_STARTED";
    public static final String IMPORT_COMPLETED = "IMPORT_COMPLETED";
    public static final String EXPORT_RUN = "EXPORT_RUN";
    public static final String API_KEY_CREATED = "API_KEY_CREATED";
    public static final String API_KEY_REVOKED = "API_KEY_REVOKED";
    public static final String WEBHOOK_CREATED = "WEBHOOK_CREATED";
    public static final String WEBHOOK_UPDATED = "WEBHOOK_UPDATED";
    public static final String SETTING_CHANGED = "SETTING_CHANGED";
    public static final String SCHEDULE_CHANGED = "SCHEDULE_CHANGED";
    public static final String APPOINTMENT_TYPE_CHANGED = "APPOINTMENT_TYPE_CHANGED";
    public static final String PROVIDER_CHANGED = "PROVIDER_CHANGED";

    @PersistenceContext
    private EntityManager em;

    public void record(DecisionContext ctx, String action, String targetType, String targetId,
                       String beforeValue, String afterValue) {
        record(ctx, action, targetType, targetId, beforeValue, afterValue, ctx == null ? null : ctx.basis());
    }

    public void record(DecisionContext ctx, String action, String targetType, String targetId,
                       String beforeValue, String afterValue, String authzBasis) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(LocalDateTime.now());
        if (ctx != null) {
            event.setActorUserId(ctx.getUserId());
            event.setActorName(ctx.getFullName());
            event.setActorRole(ctx.getRole());
            event.setIpAddress(truncate(ctx.getIpAddress(), 45));
            event.setRequestId(ctx.getRequestId());
        }
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setBeforeValue(truncate(beforeValue, 4000));
        event.setAfterValue(truncate(afterValue, 4000));
        event.setAuthzBasis(truncate(authzBasis, 500));
        em.persist(event);
    }

    public record AuditFilter(LocalDate from, LocalDate to, String action, String targetType, String actor) { }

    public List<AuditEvent> search(AuditFilter filter, int page, int pageSize) {
        StringBuilder jpql = new StringBuilder("select a from AuditEvent a where 1=1");
        if (filter.from() != null) jpql.append(" and a.occurredAt >= :from");
        if (filter.to() != null) jpql.append(" and a.occurredAt < :to");
        if (filter.action() != null && !filter.action().isBlank()) jpql.append(" and a.action = :action");
        if (filter.targetType() != null && !filter.targetType().isBlank()) jpql.append(" and a.targetType = :targetType");
        if (filter.actor() != null && !filter.actor().isBlank()) jpql.append(" and lower(a.actorName) like lower(:actor)");
        jpql.append(" order by a.occurredAt desc, a.id desc");
        TypedQuery<AuditEvent> q = em.createQuery(jpql.toString(), AuditEvent.class);
        if (filter.from() != null) q.setParameter("from", filter.from().atStartOfDay());
        if (filter.to() != null) q.setParameter("to", filter.to().plusDays(1).atStartOfDay());
        if (filter.action() != null && !filter.action().isBlank()) q.setParameter("action", filter.action());
        if (filter.targetType() != null && !filter.targetType().isBlank()) q.setParameter("targetType", filter.targetType());
        if (filter.actor() != null && !filter.actor().isBlank()) q.setParameter("actor", "%" + filter.actor().toLowerCase() + "%");
        return q.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList();
    }

    public long count(AuditFilter filter) {
        StringBuilder jpql = new StringBuilder("select count(a) from AuditEvent a where 1=1");
        if (filter.from() != null) jpql.append(" and a.occurredAt >= :from");
        if (filter.to() != null) jpql.append(" and a.occurredAt < :to");
        if (filter.action() != null && !filter.action().isBlank()) jpql.append(" and a.action = :action");
        if (filter.targetType() != null && !filter.targetType().isBlank()) jpql.append(" and a.targetType = :targetType");
        if (filter.actor() != null && !filter.actor().isBlank()) jpql.append(" and lower(a.actorName) like lower(:actor)");
        TypedQuery<Long> q = em.createQuery(jpql.toString(), Long.class);
        if (filter.from() != null) q.setParameter("from", filter.from().atStartOfDay());
        if (filter.to() != null) q.setParameter("to", filter.to().plusDays(1).atStartOfDay());
        if (filter.action() != null && !filter.action().isBlank()) q.setParameter("action", filter.action());
        if (filter.targetType() != null && !filter.targetType().isBlank()) q.setParameter("targetType", filter.targetType());
        if (filter.actor() != null && !filter.actor().isBlank()) q.setParameter("actor", "%" + filter.actor().toLowerCase() + "%");
        return q.getSingleResult();
    }

    /** Audit history for one patient (chart audit tab). Read-only. */
    public List<AuditEvent> forPatient(Long patientId, int page, int pageSize) {
        return em.createQuery(
                        "select a from AuditEvent a where (a.targetType = 'PATIENT' and a.targetId = :pid) " +
                        "or (a.targetType = 'DOCUMENT' and a.targetId in (select cast(d.id as string) from Document d where d.patient.id = :pid)) " +
                        "order by a.occurredAt desc, a.id desc", AuditEvent.class)
                .setParameter("pid", patientId.toString())
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public List<String> distinctActions() {
        return em.createQuery("select distinct a.action from AuditEvent a order by a.action", String.class).getResultList();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
