package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Secure messaging between patients and clinic staff, with internal-only
 * staff notes. Internal notes are separated from patient-visible messages
 * and are never returned to a patient principal.
 */
@Stateless
public class MessageService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private PatientAccessService patientAccess;

    @EJB
    private AuditService audit;

    @EJB
    private NotificationService notifications;

    public record ThreadView(MessageThread thread, List<Message> messages, boolean unread, long patientVisibleCount) { }

    // ------------------------------------------------------------ queries

    /** Patient's own threads (patient-visible messages only). */
    public List<MessageThread> myThreads(DecisionContext ctx) {
        Patient p = patientAccess.patientForUser(ctx);
        return em.createQuery(
                        "select t from MessageThread t where t.patient.id = :pid order by t.lastMessageAt desc",
                        MessageThread.class)
                .setParameter("pid", p.getId())
                .getResultList();
    }

    /** Staff: threads for one patient. */
    public List<MessageThread> threadsForPatient(DecisionContext ctx, long patientId) {
        patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        return em.createQuery(
                        "select t from MessageThread t where t.patient.id = :pid order by t.lastMessageAt desc",
                        MessageThread.class)
                .setParameter("pid", patientId)
                .getResultList();
    }

    /** Open a thread. Patients receive only PATIENT_VISIBLE messages; internal notes stay staff-only. */
    public ThreadView openThread(DecisionContext ctx, long threadId) {
        MessageThread t = em.find(MessageThread.class, threadId);
        if (t == null) throw new NotFoundException("Conversation not found.");
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, t.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
        } else {
            patientAccess.requirePatientAccess(ctx, t.getPatient().getId(), PatientAccessService.AccessLevel.STAFF);
        }
        List<Message> all = em.createQuery(
                        "select m from Message m where m.thread.id = :tid order by m.createdAt asc, m.id asc", Message.class)
                .setParameter("tid", threadId)
                .getResultList();
        List<Message> visible = ctx.isPatient()
                ? all.stream().filter(m -> m.getVisibility() == MessageVisibility.PATIENT_VISIBLE).toList()
                : all;
        markRead(ctx, threadId);
        return new ThreadView(t, visible, true, visible.size());
    }

    /** Start a conversation as the patient. */
    public MessageThread startThread(DecisionContext ctx, long patientId, String subject, String body) {
        ctx.require("message.send");
        patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.OWN);
        return createThread(ctx, patientId, subject, body, MessageVisibility.PATIENT_VISIBLE, false);
    }

    /** Staff: start a conversation with a patient. */
    public MessageThread staffStartThread(DecisionContext ctx, long patientId, String subject, String body) {
        ctx.require("message.send");
        Patient p = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        return createThread(ctx, p.getId(), subject, body, MessageVisibility.PATIENT_VISIBLE, true);
    }

    private MessageThread createThread(DecisionContext ctx, long patientId, String subject, String body,
                                       MessageVisibility visibility, boolean staffSender) {
        validateMessage(ctx, subject, body);
        Patient patient = em.find(Patient.class, patientId);
        MessageThread t = new MessageThread();
        t.setPatient(patient);
        t.setSubject(subject.strip());
        t.setCreatedBy(ctx.getUserId());
        t.setCreatedAt(LocalDateTime.now());
        t.setLastMessageAt(LocalDateTime.now());
        em.persist(t);
        Message m = new Message();
        m.setThread(t);
        m.setSenderUserId(ctx.getUserId());
        m.setVisibility(visibility);
        m.setBody(body.strip());
        m.setCreatedAt(LocalDateTime.now());
        em.persist(m);
        audit.record(ctx, AuditService.MESSAGE_SENT, "MESSAGE_THREAD", String.valueOf(t.getId()),
                null, Json.obj("subject", t.getSubject(), "visibility", visibility.name()));
        afterMessage(ctx, t, patient, staffSender);
        return t;
    }

    /** Reply in a thread (patient or staff, always patient-visible). */
    public Message reply(DecisionContext ctx, long threadId, String body) {
        ctx.require("message.send");
        MessageThread t = em.find(MessageThread.class, threadId);
        if (t == null) throw new NotFoundException("Conversation not found.");
        boolean staffSender;
        if (ctx.isPatient()) {
            patientAccess.requirePatientAccess(ctx, t.getPatient().getId(), PatientAccessService.AccessLevel.OWN);
            staffSender = false;
        } else {
            patientAccess.requirePatientAccess(ctx, t.getPatient().getId(), PatientAccessService.AccessLevel.STAFF);
            staffSender = true;
        }
        if (body == null || body.isBlank()) throw new ValidationException("Write a message first.");
        checkLength(ctx, body);
        Message m = new Message();
        m.setThread(t);
        m.setSenderUserId(ctx.getUserId());
        m.setVisibility(MessageVisibility.PATIENT_VISIBLE);
        m.setBody(body.strip());
        m.setCreatedAt(LocalDateTime.now());
        t.setLastMessageAt(LocalDateTime.now());
        em.persist(m);
        audit.record(ctx, AuditService.MESSAGE_SENT, "MESSAGE_THREAD", String.valueOf(threadId),
                null, Json.obj("subject", t.getSubject()));
        afterMessage(ctx, t, t.getPatient(), staffSender);
        return m;
    }

    /** Internal staff note on a thread: never visible to the patient. */
    public Message addInternalNote(DecisionContext ctx, long threadId, String body) {
        ctx.require("message.note.internal");
        MessageThread t = em.find(MessageThread.class, threadId);
        if (t == null) throw new NotFoundException("Conversation not found.");
        patientAccess.requirePatientAccess(ctx, t.getPatient().getId(), PatientAccessService.AccessLevel.STAFF);
        if (body == null || body.isBlank()) throw new ValidationException("Write a note first.");
        checkLength(ctx, body);
        Message m = new Message();
        m.setThread(t);
        m.setSenderUserId(ctx.getUserId());
        m.setVisibility(MessageVisibility.INTERNAL);
        m.setBody(body.strip());
        m.setCreatedAt(LocalDateTime.now());
        t.setLastMessageAt(LocalDateTime.now());
        em.persist(m);
        audit.record(ctx, AuditService.NOTE_ADDED, "MESSAGE_THREAD", String.valueOf(threadId),
                null, Json.obj("subject", t.getSubject(), "visibility", "INTERNAL"));
        return m;
    }

    private void afterMessage(DecisionContext ctx, MessageThread t, Patient patient, boolean staffSender) {
        if (staffSender) {
            if (patient.getUser() != null) {
                notifications.notify(patient.getUser().getId(), NotificationType.MESSAGE,
                        "New message from the clinic",
                        "The clinic replied to '" + t.getSubject() + "'.",
                        "/app/patient/messages.xhtml?thread=" + t.getId());
            }
        } else {
            notifications.notifyStaff(ctx.getUserId(), NotificationType.MESSAGE,
                    "New patient message",
                    patient.fullName() + " sent a message: '" + t.getSubject() + "'.",
                    "/app/staff/messages.xhtml?thread=" + t.getId());
        }
    }

    // ------------------------------------------------------------ read state

    public boolean isUnread(DecisionContext ctx, MessageThread thread) {
        if (ctx.getUserId() == null) return false;
        ThreadReadState state = em.createQuery(
                        "select r from ThreadReadState r where r.thread.id = :tid and r.userId = :uid", ThreadReadState.class)
                .setParameter("tid", thread.getId())
                .setParameter("uid", ctx.getUserId())
                .getResultStream().findFirst().orElse(null);
        LocalDateTime lastRead = state == null ? thread.getCreatedAt().minusSeconds(1) : state.getLastReadAt();
        Long newer = em.createQuery(
                        "select count(m) from Message m where m.thread.id = :tid and m.createdAt > :read", Long.class)
                .setParameter("tid", thread.getId())
                .setParameter("read", lastRead)
                .getSingleResult();
        return newer != null && newer > 0;
    }

    public long unreadThreadCount(DecisionContext ctx) {
        if (ctx.getUserId() == null) return 0;
        List<MessageThread> threads;
        if (ctx.isPatient()) {
            threads = myThreads(ctx);
        } else {
            threads = em.createQuery(
                            "select t from MessageThread t where exists (select m from Message m where m.thread = t and m.visibility = :v) order by t.lastMessageAt desc",
                            MessageThread.class)
                    .setParameter("v", MessageVisibility.PATIENT_VISIBLE)
                    .getResultList();
        }
        long count = 0;
        for (MessageThread t : threads) {
            if (isUnread(ctx, t)) count++;
        }
        return count;
    }

    public void markRead(DecisionContext ctx, long threadId) {
        if (ctx.getUserId() == null) return;
        ThreadReadState state = em.createQuery(
                        "select r from ThreadReadState r where r.thread.id = :tid and r.userId = :uid", ThreadReadState.class)
                .setParameter("tid", threadId)
                .setParameter("uid", ctx.getUserId())
                .getResultStream().findFirst().orElse(null);
        if (state == null) {
            state = new ThreadReadState();
            state.setThread(em.find(MessageThread.class, threadId));
            state.setUserId(ctx.getUserId());
            state.setLastReadAt(LocalDateTime.now());
            em.persist(state);
        } else {
            state.setLastReadAt(LocalDateTime.now());
        }
    }

    // ------------------------------------------------------------ helpers

    private void validateMessage(DecisionContext ctx, String subject, String body) {
        if (subject == null || subject.isBlank() || subject.length() > 200) {
            throw new ValidationException("Subject is required (max 200 characters).");
        }
        if (body == null || body.isBlank()) throw new ValidationException("Write a message first.");
        checkLength(ctx, body);
    }

    private void checkLength(DecisionContext ctx, String body) {
        int max = ctx.limitInt("message.max_length", 4000);
        if (body.length() > max) {
            throw new ValidationException("Message is too long (max " + max + " characters).");
        }
    }
}
