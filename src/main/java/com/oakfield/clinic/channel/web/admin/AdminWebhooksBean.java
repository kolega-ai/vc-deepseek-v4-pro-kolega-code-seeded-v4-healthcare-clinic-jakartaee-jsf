package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.WebhookDelivery;
import com.oakfield.clinic.model.WebhookEcho;
import com.oakfield.clinic.model.WebhookSubscription;
import com.oakfield.clinic.svc.IntegrationService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/** Clinic-admin webhook subscriptions, delivery log, and the demo echo receiver. */
@Named("adminWebhooks")
@ViewScoped
public class AdminWebhooksBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private IntegrationService integrations;

    private List<WebhookSubscription> subs;
    private List<WebhookDelivery> deliveries;
    private List<WebhookEcho> echoes;

    // create form
    private String url;
    private String secret;
    private List<String> events;

    @PostConstruct
    public void load() {
        subs = integrations.listSubscriptions();
        deliveries = integrations.listDeliveries(0, 30);
        echoes = integrations.listEchoes(0, 30);
    }

    public void createSubscription() {
        try {
            integrations.createSubscription(session.ctx(), url, secret, events);
            FacesUtil.info("Webhook created", "Deliveries will be signed with HMAC-SHA256.");
            url = null;
            secret = null;
            events = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not create webhook", e.getMessage());
        }
    }

    public void toggle(long id) {
        WebhookSubscription sub = subs == null ? null
                : subs.stream().filter(s -> s.getId().equals(id)).findFirst().orElse(null);
        if (sub == null) return;
        try {
            integrations.updateSubscription(session.ctx(), id, !sub.isActive());
            FacesUtil.info(sub.isActive() ? "Webhook disabled" : "Webhook enabled",
                    "Subscription updated.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not update webhook", e.getMessage());
        }
    }

    public void test(long id) {
        try {
            String result = integrations.testSubscription(session.ctx(), id);
            FacesUtil.info("Webhook test", result);
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Webhook test failed", e.getMessage());
        }
    }

    public List<String> getSupportedEvents() {
        return IntegrationService.SUPPORTED_EVENTS.stream().sorted().toList();
    }

    public List<WebhookSubscription> getSubs() { return subs; }
    public List<WebhookDelivery> getDeliveries() { return deliveries; }
    public List<WebhookEcho> getEchoes() { return echoes; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public List<String> getEvents() { return events; }
    public void setEvents(List<String> events) { this.events = events; }
}
