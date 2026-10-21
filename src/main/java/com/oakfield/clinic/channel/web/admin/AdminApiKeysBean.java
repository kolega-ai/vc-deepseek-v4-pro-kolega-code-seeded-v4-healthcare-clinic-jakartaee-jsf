package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.decision.DecisionService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** Clinic-admin API keys: create (one-time plaintext), list, revoke/activate. */
@Named("adminApiKeys")
@ViewScoped
public class AdminApiKeysBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private DecisionService decision;

    private List<DecisionService.ApiKeyInfo> keys;

    // create form
    private String name;
    private List<String> scopes;
    private int expiresDays;
    private String newKey;

    @PostConstruct
    public void load() {
        keys = decision.listApiKeys();
    }

    public void createKey() {
        if (name == null || name.isBlank()) {
            FacesUtil.error("Name required", "Give the key a recognizable name.");
            return;
        }
        if (scopes == null || scopes.isEmpty()) {
            FacesUtil.error("Pick at least one scope", "A key needs at least one scope.");
            return;
        }
        try {
            DecisionService.ApiKeyCreation created = decision.createApiKey(name, scopes,
                    session.ctx().getUserId(),
                    expiresDays <= 0 ? null : LocalDateTime.now().plusDays(expiresDays));
            newKey = created.plaintextKey();
            name = null;
            scopes = null;
            expiresDays = 0;
            load();
        } catch (RuntimeException e) {
            FacesUtil.error("Could not create API key", e.getMessage());
        }
    }

    public void dismissNewKey() {
        newKey = null;
    }

    public void setActive(long id, boolean active) {
        try {
            decision.setApiKeyActive(id, active);
            FacesUtil.info(active ? "API key activated" : "API key revoked",
                    active ? "The key will be accepted again." : "The key no longer authenticates.");
            load();
        } catch (RuntimeException e) {
            FacesUtil.error("Could not update API key", e.getMessage());
        }
    }

    public List<String> getAllowedScopes() {
        return DecisionService.ALLOWED_API_KEY_SCOPES.stream().sorted().toList();
    }

    public List<DecisionService.ApiKeyInfo> getKeys() { return keys; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }
    public int getExpiresDays() { return expiresDays; }
    public void setExpiresDays(int expiresDays) { this.expiresDays = expiresDays; }
    public String getNewKey() { return newKey; }
    public void setNewKey(String newKey) { this.newKey = newKey; }
}
