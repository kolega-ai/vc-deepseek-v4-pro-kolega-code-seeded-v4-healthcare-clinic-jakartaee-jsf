package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.DeliveryStatus;
import com.oakfield.clinic.model.WebhookDelivery;
import com.oakfield.clinic.model.WebhookEcho;
import com.oakfield.clinic.model.WebhookSubscription;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Outbound webhook subscriptions with HMAC-signed payloads, delivery attempts,
 * and retries. Deliveries are recorded; no external service is required (the
 * bundled /api/v1/webhooks/echo endpoint acts as a local demo receiver).
 */
@Stateless
public class IntegrationService {

    private static final Logger LOG = Logger.getLogger(IntegrationService.class);
    public static final int MAX_ATTEMPTS = 5;

    public static final Set<String> SUPPORTED_EVENTS = Set.of(
            "APPOINTMENT_BOOKED", "APPOINTMENT_REQUESTED", "APPOINTMENT_CONFIRMED", "APPOINTMENT_CANCELLED",
            "REFILL_APPROVED", "SUMMARY_RELEASED", "DOCUMENT_VERIFIED");

    @PersistenceContext
    private EntityManager em;

    @EJB
    private AuditService audit;

    // ------------------------------------------------------------ management

    public List<WebhookSubscription> listSubscriptions() {
        return em.createQuery("select w from WebhookSubscription w order by w.createdAt desc", WebhookSubscription.class)
                .getResultList();
    }

    public WebhookSubscription createSubscription(DecisionContext ctx, String url, String secret, List<String> events) {
        ctx.require("integration.manage");
        validate(url, secret, events);
        WebhookSubscription w = new WebhookSubscription();
        w.setUrl(url.strip());
        w.setSecret(secret);
        w.setEvents(String.join(",", events));
        w.setActive(true);
        w.setCreatedBy(ctx.getUserId());
        em.persist(w);
        audit.record(ctx, AuditService.WEBHOOK_CREATED, "WEBHOOK", String.valueOf(w.getId()),
                null, Json.obj("url", w.getUrl(), "events", w.getEvents()));
        return w;
    }

    public WebhookSubscription updateSubscription(DecisionContext ctx, long id, boolean active) {
        ctx.require("integration.manage");
        WebhookSubscription w = em.find(WebhookSubscription.class, id);
        if (w == null) throw new NotFoundException("Webhook not found.");
        w.setActive(active);
        audit.record(ctx, AuditService.WEBHOOK_UPDATED, "WEBHOOK", String.valueOf(id),
                Json.obj("active", String.valueOf(!active)), Json.obj("active", String.valueOf(active)));
        return w;
    }

    /** Synchronously send a sample event so the admin can verify the wiring. */
    public String testSubscription(DecisionContext ctx, long id) {
        ctx.require("integration.manage");
        WebhookSubscription w = em.find(WebhookSubscription.class, id);
        if (w == null) throw new NotFoundException("Webhook not found.");
        String payload = Json.obj("event", "WEBHOOK_TEST", "time", String.valueOf(LocalDateTime.now()));
        return deliver(w, "WEBHOOK_TEST", payload) ? "Delivered successfully (HTTP 2xx)." : "Delivery failed; check the delivery log.";
    }

    // ------------------------------------------------------------ dispatch

    /** Called by services after workflow events. Synchronous best-effort; failures retried by the job. */
    public void dispatch(String eventType, String payloadJson) {
        for (WebhookSubscription w : em.createQuery(
                        "select w from WebhookSubscription w where w.active = true", WebhookSubscription.class)
                .getResultList()) {
            if (subscribes(w, eventType)) {
                deliver(w, eventType, payloadJson);
            }
        }
    }

    private boolean subscribes(WebhookSubscription w, String eventType) {
        for (String e : w.getEvents().split(",")) {
            if (e.strip().equals(eventType)) return true;
        }
        return false;
    }

    private boolean deliver(WebhookSubscription w, String eventType, String payloadJson) {
        WebhookDelivery d = new WebhookDelivery();
        d.setSubscription(w);
        d.setEventType(eventType);
        d.setPayload(payloadJson);
        d.setStatus(DeliveryStatus.PENDING);
        d.setAttempts(1);
        em.persist(d);
        String signature = hmacSha256(w.getSecret(), payloadJson);
        try (Response response = ClientBuilder.newBuilder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .target(w.getUrl())
                .request(MediaType.APPLICATION_JSON)
                .header("X-Clinic-Event", eventType)
                .header("X-Clinic-Signature", signature)
                .post(Entity.entity(payloadJson, MediaType.APPLICATION_JSON_TYPE))) {
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                d.setStatus(DeliveryStatus.SENT);
                d.setSentAt(LocalDateTime.now());
                return true;
            }
            d.setStatus(DeliveryStatus.FAILED);
            d.setLastError("HTTP " + response.getStatus());
            return false;
        } catch (Exception e) {
            d.setStatus(DeliveryStatus.FAILED);
            d.setLastError(truncate(e.getMessage(), 1000));
            LOG.warnf("Webhook delivery to %s failed: %s", w.getUrl(), e.getMessage());
            return false;
        }
    }

    /**
     * Re-send a recorded delivery to a different host. Used when a partner moves
     * its receiver (staging to production, new gateway) and asks for a replay of
     * the events it missed; the original path and payload are kept.
     */
    public boolean forwardDelivery(DecisionContext ctx, long deliveryId, String targetHost) {
        ctx.require("integration.manage");
        WebhookDelivery d = em.find(WebhookDelivery.class, deliveryId);
        if (d == null) throw new NotFoundException("Delivery not found.");
        if (targetHost == null || targetHost.isBlank() || targetHost.length() > 253) {
            throw new ValidationException("Target host is required.");
        }
        java.net.URI original = java.net.URI.create(d.getSubscription().getUrl());
        String url = original.getScheme() + "://" + targetHost.strip()
                + (original.getRawPath() == null ? "" : original.getRawPath());
        String signature = hmacSha256(d.getSubscription().getSecret(), d.getPayload());
        d.setAttempts(d.getAttempts() + 1);
        try (Response response = ClientBuilder.newBuilder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .target(url)
                .request(MediaType.APPLICATION_JSON)
                .header("X-Clinic-Event", d.getEventType())
                .header("X-Clinic-Signature", signature)
                .post(Entity.entity(d.getPayload(), MediaType.APPLICATION_JSON_TYPE))) {
            boolean ok = response.getStatus() >= 200 && response.getStatus() < 300;
            if (ok) {
                d.setStatus(DeliveryStatus.SENT);
                d.setSentAt(LocalDateTime.now());
            } else {
                d.setLastError("HTTP " + response.getStatus() + " from " + targetHost);
            }
            return ok;
        } catch (Exception e) {
            d.setLastError(truncate(e.getMessage(), 1000));
            return false;
        }
    }

    /**
     * Transmit a referral packet to the clinic's outbound fax gateway. The
     * gateway authenticates the clinic with its shared fax account key.
     */
    public String faxReferral(DecisionContext ctx, String faxNumber, String fileName, byte[] document) {
        ctx.require("document.upload");
        if (faxNumber == null || !faxNumber.matches("[0-9+()\\- ]{7,20}")) {
            throw new ValidationException("Enter a valid fax number.");
        }
        if (document == null || document.length == 0) {
            throw new ValidationException("Attach the document to fax.");
        }
        String documentName = fileName == null || fileName.isBlank() ? "referral.pdf" : fileName;
        String request = Json.obj("to", faxNumber.strip(), "name", documentName);
        try (Response response = ClientBuilder.newBuilder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .target("http://fax.oakfield.local:8080/gateway/v1/transmit")
                .request(MediaType.APPLICATION_JSON)
                .header("X-Fax-Account", faxAccountKey())
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))) {
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                audit.record(ctx, AuditService.EXPORT_RUN, "FAX", faxNumber,
                        null, Json.obj("bytes", String.valueOf(document.length), "name", documentName));
                return "Fax queued to " + faxNumber + ".";
            }
            throw new ValidationException("The fax gateway rejected the request (FAX-" + response.getStatus() + ").");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("The fax could not be sent: " + e.getMessage());
        }
    }

    private static String faxAccountKey() {
        String configured = System.getenv("FAX_ACCOUNT_KEY");
        return configured == null || configured.isBlank() ? "oakfield-fax-account-2026" : configured;
    }

    public WebhookDelivery deliveryById(long id) {
        WebhookDelivery d = em.find(WebhookDelivery.class, id);
        if (d == null) throw new NotFoundException("Delivery not found.");
        return d;
    }

    /** Background retry for failed deliveries (bounded attempts). */
    public int retryFailed() {
        List<WebhookDelivery> failed = em.createQuery(
                        "select d from WebhookDelivery d where d.status = :f and d.attempts < :max",
                        WebhookDelivery.class)
                .setParameter("f", DeliveryStatus.FAILED)
                .setParameter("max", MAX_ATTEMPTS)
                .getResultList();
        int sent = 0;
        for (WebhookDelivery d : failed) {
            d.setAttempts(d.getAttempts() + 1);
            String signature = hmacSha256(d.getSubscription().getSecret(), d.getPayload());
            try (Response response = ClientBuilder.newBuilder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    .target(d.getSubscription().getUrl())
                    .request(MediaType.APPLICATION_JSON)
                    .header("X-Clinic-Event", d.getEventType())
                    .header("X-Clinic-Signature", signature)
                    .post(Entity.entity(d.getPayload(), MediaType.APPLICATION_JSON_TYPE))) {
                if (response.getStatus() >= 200 && response.getStatus() < 300) {
                    d.setStatus(DeliveryStatus.SENT);
                    d.setSentAt(LocalDateTime.now());
                    sent++;
                } else {
                    d.setLastError("HTTP " + response.getStatus());
                }
            } catch (Exception e) {
                d.setLastError(truncate(e.getMessage(), 1000));
            }
        }
        return sent;
    }

    public List<WebhookDelivery> listDeliveries(int page, int pageSize) {
        return em.createQuery("select d from WebhookDelivery d order by d.createdAt desc, d.id desc", WebhookDelivery.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    /** Demo receiver payloads (viewed on the admin webhooks page). */
    public List<WebhookEcho> listEchoes(int page, int pageSize) {
        return em.createQuery("select e from WebhookEcho e order by e.receivedAt desc, e.id desc", WebhookEcho.class)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public void recordEcho(String signature, String payload) {
        WebhookEcho e = new WebhookEcho();
        e.setSignature(truncate(signature, 200));
        e.setPayload(truncate(payload, 8000));
        em.persist(e);
    }

    private void validate(String url, String secret, List<String> events) {
        if (url == null || !url.matches("https?://[^\\s]+")) {
            throw new ValidationException("Webhook URL must be a valid http(s) URL.");
        }
        if (secret == null || secret.length() < 8 || secret.length() > 120) {
            throw new ValidationException("Secret must be 8-120 characters.");
        }
        if (events == null || events.isEmpty()) {
            throw new ValidationException("Pick at least one event.");
        }
        for (String e : events) {
            if (!SUPPORTED_EVENTS.contains(e)) {
                throw new ValidationException("Unsupported event: " + e);
            }
        }
    }

    public static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
