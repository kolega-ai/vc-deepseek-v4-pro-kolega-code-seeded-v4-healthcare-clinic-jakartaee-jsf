package com.oakfield.clinic.decision;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable authorization context resolved by the decision tier from its own
 * database (decision_session + role/scope/limit tables). Channel code never
 * constructs one from client-supplied attributes.
 */
public final class DecisionContext {

    public static final String ROLE_SYSTEM = "SYSTEM";

    private final Long userId;              // null for the SYSTEM context
    private final String email;
    private final String fullName;
    private final String role;              // AppRole name or ROLE_SYSTEM
    private final Set<String> scopes;
    private final Map<String, String> limits;
    private final long sessionId;           // decision_session.id, 0 for SYSTEM
    private final String tokenId;
    private final String ipAddress;
    private final String requestId;

    public DecisionContext(Long userId, String email, String fullName, String role,
                           Set<String> scopes, Map<String, String> limits,
                           long sessionId, String tokenId, String ipAddress, String requestId) {
        this.userId = userId;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
        this.limits = Collections.unmodifiableMap(new LinkedHashMap<>(limits));
        this.sessionId = sessionId;
        this.tokenId = tokenId;
        this.ipAddress = ipAddress;
        this.requestId = requestId;
    }

    /** Context used by scheduled jobs and the seed service (not reachable from any channel). */
    public static DecisionContext system(String requestId) {
        return new DecisionContext(null, "system", "System Job", ROLE_SYSTEM,
                Collections.singleton("*"), Collections.emptyMap(), 0, null, "local", requestId);
    }

    public boolean hasScope(String scope) {
        return scopes.contains("*") || scopes.contains(scope);
    }

    public void require(String scope) {
        if (!hasScope(scope)) {
            throw new AccessDeniedException("Scope '" + scope + "' is required; acting role '" + role + "' lacks it.");
        }
    }

    public String limit(String name, String defaultValue) {
        return limits.getOrDefault(name, defaultValue);
    }

    public int limitInt(String name, int defaultValue) {
        String v = limits.get(name);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean isPatient() { return "PATIENT".equals(role); }
    public boolean isStaff() { return !isPatient() && !ROLE_SYSTEM.equals(role); }
    public boolean isSystem() { return ROLE_SYSTEM.equals(role); }

    /** Human-readable authorization basis recorded in audit rows. */
    public String basis() {
        return "role=" + role + ";scopes=" + String.join(",", scopes) + ";session=" + sessionId;
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public Set<String> getScopes() { return scopes; }
    public Map<String, String> getLimits() { return limits; }
    public long getSessionId() { return sessionId; }
    public String getTokenId() { return tokenId; }
    public String getIpAddress() { return ipAddress; }
    public String getRequestId() { return requestId; }
}
