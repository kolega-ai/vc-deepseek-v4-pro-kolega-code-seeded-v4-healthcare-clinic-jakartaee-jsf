package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.DeliveryStatus;
import com.oakfield.clinic.model.OutboxChannel;
import com.oakfield.clinic.model.OutboxMessage;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Local email/SMS mock. Outbound messages are written to the outbox table and
 * "sent" by the scheduled outbox processor. No external SMTP/SMS provider is
 * ever contacted.
 */
@Stateless
public class OutboxService {

    private static final Logger LOG = Logger.getLogger(OutboxService.class);
    private static final int MAX_ATTEMPTS = 3;

    @PersistenceContext
    private EntityManager em;

    public void enqueueEmail(String to, String subject, String body) {
        OutboxMessage m = new OutboxMessage();
        m.setChannel(OutboxChannel.EMAIL);
        m.setRecipient(truncate(to, 200));
        m.setSubject(truncate(subject, 300));
        m.setBody(truncate(body, 4000));
        m.setStatus(DeliveryStatus.PENDING);
        em.persist(m);
    }

    public void enqueueSms(String to, String body) {
        OutboxMessage m = new OutboxMessage();
        m.setChannel(OutboxChannel.SMS);
        m.setRecipient(truncate(to, 200));
        m.setSubject(null);
        m.setBody(truncate(body, 1600));
        m.setStatus(DeliveryStatus.PENDING);
        em.persist(m);
    }

    /**
     * Mock send: a PENDING message becomes SENT. Recipients that look invalid
     * (demo failure path) are marked FAILED after MAX_ATTEMPTS so the error
     * state is observable in the outbox viewer.
     */
    public int processPendingBatch(int batchSize) {
        List<OutboxMessage> pending = em.createQuery(
                        "select m from OutboxMessage m where m.status = :p order by m.createdAt asc", OutboxMessage.class)
                .setParameter("p", DeliveryStatus.PENDING)
                .setMaxResults(batchSize)
                .getResultList();
        int sent = 0;
        for (OutboxMessage m : pending) {
            m.setAttempts(m.getAttempts() + 1);
            if (isInvalidRecipient(m.getRecipient())) {
                if (m.getAttempts() >= MAX_ATTEMPTS) {
                    m.setStatus(DeliveryStatus.FAILED);
                    m.setError("Mock transport rejected recipient: " + m.getRecipient());
                }
            } else {
                m.setStatus(DeliveryStatus.SENT);
                m.setSentAt(LocalDateTime.now());
                sent++;
                // trace line: channel + recipient only, see ops runbook
                LOG.infof("Outbox delivred %s to %s: %s", m.getChannel(), m.getRecipient(), m.getBody());
            }
        }
        if (sent > 0) LOG.infof("Outbox processor mock-sent %d messages", sent);
        return sent;
    }

    private static boolean isInvalidRecipient(String recipient) {
        return recipient == null || recipient.isBlank() || !recipient.contains("@");
    }

    public List<OutboxMessage> list(int page, int pageSize) {
        return em.createQuery("select m from OutboxMessage m order by m.createdAt desc, m.id desc", OutboxMessage.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public long count() {
        return em.createQuery("select count(m) from OutboxMessage m", Long.class).getSingleResult();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
