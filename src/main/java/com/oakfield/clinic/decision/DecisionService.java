package com.oakfield.clinic.decision;

import com.oakfield.clinic.model.*;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.jboss.logging.Logger;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Decision tier: the sole authority for authorization.
 *
 * <ul>
 *   <li>Mints opaque, HMAC-signed session tokens at login. The channel tier
 *       authenticates credentials; this tier decides and records what the
 *       session may do.</li>
 *   <li>Resolves every token against its OWN tables (decision_session +
 *       decision_role_scope + decision_scope_limit) on every request and
 *       re-checks account status. Client-supplied identity attributes are
 *       ignored: role, scopes, and limits are only ever read from these rows.</li>
 *   <li>Mints signed scope tokens for the vendor-core mock; core-mock verifies
 *       the signature and enforces the scope claim.</li>
 * </ul>
 */
@Stateless
public class DecisionService {

    private static final Logger LOG = Logger.getLogger(DecisionService.class);

    private static final Duration SESSION_TTL = Duration.ofHours(12);
    /** "Keep me signed in" on the patient portal: the chart stays open across visits without re-entering credentials. */
    static final Duration PATIENT_PORTAL_REMEMBER_TTL = Duration.ofDays(90);
    private static final Duration CORE_TOKEN_TTL = Duration.ofMinutes(10);

    /** API-key scopes that may be granted through the admin screen. */
    public static final Set<String> ALLOWED_API_KEY_SCOPES =
            Set.of("availability.read", "appointment.request", "export.read");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @PersistenceContext
    private EntityManager em;

    // ---------------------------------------------------------------- env

    public String decisionSecret() {
        String s = System.getenv("DECISION_TOKEN_SECRET");
        if (s == null || s.isBlank()) {
            LOG.warn("DECISION_TOKEN_SECRET not set - using insecure dev default. Set it in any real deployment.");
            return "dev-only-insecure-decision-secret";
        }
        return s;
    }

    public String coreMockKey() {
        String s = System.getenv("CORE_MOCK_API_KEY");
        if (s == null || s.isBlank()) {
            LOG.warn("CORE_MOCK_API_KEY not set - using insecure dev default. Set it in any real deployment.");
            return "dev-only-insecure-core-mock-key";
        }
        return s;
    }

    // ---------------------------------------------------------------- sessions

    public record SessionIssue(String token, String tokenId, DecisionContext context) { }

    /** Mint a decision session for a successfully authenticated user (called once per login). */
    public SessionIssue issueSession(User user, String ipAddress, String userAgent) {
        return issueSession(user, ipAddress, userAgent, false);
    }

    /** Mint a decision session; {@code remembered} sessions use the long portal lifetime. */
    public SessionIssue issueSession(User user, String ipAddress, String userAgent, boolean remembered) {
        String tokenId = B64.encodeToString(randomBytes(32));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plus(remembered ? PATIENT_PORTAL_REMEMBER_TTL : SESSION_TTL);

        Set<String> scopes = scopesForRole(user.getRole());
        Map<String, String> limits = limitsForRole(user.getRole());

        DecisionSession session = new DecisionSession();
        session.setTokenId(tokenId);
        session.setUserId(user.getId());
        session.setEmail(user.getEmail());
        session.setFullName(user.getFullName());
        session.setRole(user.getRole().name());
        session.setIssuedAt(now);
        session.setExpiresAt(expires);
        session.setIpAddress(truncate(ipAddress, 45));
        session.setUserAgent(truncate(userAgent, 300));
        session.setScopes(encodeScopes(scopes));
        session.setLimits(encodeLimits(limits));
        em.persist(session);

        String token = TokenCodec.signSession(decisionSecret(), tokenId, toEpochSeconds(expires));
        DecisionContext ctx = new DecisionContext(user.getId(), user.getEmail(), user.getFullName(),
                user.getRole().name(), scopes, limits, session.getId(), tokenId, ipAddress, null);
        return new SessionIssue(token, tokenId, ctx);
    }

    /** Resolve an opaque session token against decision-owned tables. Runs on every request. */
    public DecisionContext resolve(String token, String ipAddress, String requestId) {
        if (token == null || token.isBlank()) {
            throw new NotAuthenticatedException("No session token supplied.");
        }
        TokenCodec.SessionTokenParts parts = TokenCodec.parseAndVerifySession(decisionSecret(), token);
        DecisionSession session = em.createQuery(
                        "select s from DecisionSession s where s.tokenId = :tid", DecisionSession.class)
                .setParameter("tid", parts.tokenId())
                .getResultStream().findFirst()
                .orElseThrow(() -> new NotAuthenticatedException("Unknown session."));
        if (session.getRevokedAt() != null) {
            throw new NotAuthenticatedException("Session revoked.");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setRevokedAt(LocalDateTime.now());
            throw new NotAuthenticatedException("Session expired.");
        }
        // Defense in depth: an account disabled after login loses access immediately.
        User user = em.find(User.class, session.getUserId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            session.setRevokedAt(LocalDateTime.now());
            throw new NotAuthenticatedException("Account is not active.");
        }
        return new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                decodeScopes(session.getScopes()), decodeLimits(session.getLimits()),
                session.getId(), session.getTokenId(), ipAddress, requestId);
    }

    public void revoke(long sessionId) {
        DecisionSession session = em.find(DecisionSession.class, sessionId);
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(LocalDateTime.now());
        }
    }

    /** Revoke every live session for a user (password change, disable, role change). */
    public void revokeAllForUser(Long userId) {
        em.createQuery("update DecisionSession s set s.revokedAt = :now where s.userId = :uid and s.revokedAt is null")
                .setParameter("now", LocalDateTime.now())
                .setParameter("uid", userId)
                .executeUpdate();
    }

    /** Revoke every live session except the caller's current one (own password change). */
    public void revokeAllForUserExcept(Long userId, long keepSessionId) {
        em.createQuery("update DecisionSession s set s.revokedAt = :now where s.userId = :uid and s.revokedAt is null and s.id <> :keep")
                .setParameter("now", LocalDateTime.now())
                .setParameter("uid", userId)
                .setParameter("keep", keepSessionId)
                .executeUpdate();
    }

    public void revokeExpiredSessions() {
        em.createQuery("update DecisionSession s set s.revokedAt = :now where s.expiresAt < :now and s.revokedAt is null")
                .setParameter("now", LocalDateTime.now())
                .executeUpdate();
    }

    // ---------------------------------------------------------------- scopes / limits

    public Set<String> scopesForRole(AppRole role) {
        Set<String> scopes = new LinkedHashSet<>();
        em.createQuery("select r.scope from DecisionRoleScope r where r.role = :role", String.class)
                .setParameter("role", role.name())
                .getResultStream().forEach(scopes::add);
        return scopes;
    }

    public Map<String, String> limitsForRole(AppRole role) {
        Map<String, String> limits = new LinkedHashMap<>();
        for (DecisionScopeLimit l : em.createQuery(
                "select l from DecisionScopeLimit l where l.role = :role", DecisionScopeLimit.class)
                .setParameter("role", role.name()).getResultList()) {
            limits.put(l.getLimitName(), l.getLimitValue());
        }
        return limits;
    }

    // ---------------------------------------------------------------- core-mock scope tokens

    /** Mint a signed scope token for a vendor-core call. core-mock verifies and enforces it. */
    public String mintCoreToken(String scope, String subject) {
        long expiry = toEpochSeconds(LocalDateTime.now().plus(CORE_TOKEN_TTL));
        return TokenCodec.signCoreToken(coreMockKey(), scope, subject, expiry);
    }

    public TokenCodec.CoreTokenParts verifyCoreToken(String token) {
        return TokenCodec.parseAndVerifyCore(coreMockKey(), token);
    }

    // ---------------------------------------------------------------- API keys

    public record ApiKeyInfo(long id, String name, String keyPrefix, Set<String> scopes, boolean active,
                             LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime lastUsedAt) { }

    public record ApiKeyCreation(long id, String plaintextKey) { }

    public ApiKeyCreation createApiKey(String name, Collection<String> scopes, Long createdBy, LocalDateTime expiresAt) {
        for (String s : scopes) {
            if (!ALLOWED_API_KEY_SCOPES.contains(s)) {
                throw new IllegalArgumentException("API key scope not allowed: " + s);
            }
        }
        if (scopes.isEmpty()) throw new IllegalArgumentException("At least one scope is required.");
        String plaintext = "ok_" + B64.encodeToString(randomBytes(32));
        DecisionApiKey key = new DecisionApiKey();
        key.setName(truncate(name, 120));
        key.setKeyHash(TokenCodec.sha256Hex(plaintext));
        key.setKeyPrefix(plaintext.substring(0, Math.min(10, plaintext.length())));
        key.setScopes(String.join(",", scopes));
        key.setActive(true);
        key.setCreatedBy(createdBy);
        key.setExpiresAt(expiresAt);
        em.persist(key);
        return new ApiKeyCreation(key.getId(), plaintext);
    }

    /** Resolve an API key header value. Only the hash is compared. */
    public Optional<ApiKeyInfo> resolveApiKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return Optional.empty();
        DecisionApiKey key = em.createQuery(
                        "select k from DecisionApiKey k where k.keyHash = :h", DecisionApiKey.class)
                .setParameter("h", TokenCodec.sha256Hex(rawKey))
                .getResultStream().findFirst().orElse(null);
        if (key == null || !key.isActive()) return Optional.empty();
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) return Optional.empty();
        key.setLastUsedAt(LocalDateTime.now());
        return Optional.of(new ApiKeyInfo(key.getId(), key.getName(), key.getKeyPrefix(),
                decodeScopes(key.getScopes()), key.isActive(), key.getCreatedAt(), key.getExpiresAt(), key.getLastUsedAt()));
    }

    public List<ApiKeyInfo> listApiKeys() {
        return em.createQuery("select k from DecisionApiKey k order by k.createdAt desc", DecisionApiKey.class)
                .getResultList().stream()
                .map(k -> new ApiKeyInfo(k.getId(), k.getName(), k.getKeyPrefix(), decodeScopes(k.getScopes()),
                        k.isActive(), k.getCreatedAt(), k.getExpiresAt(), k.getLastUsedAt()))
                .toList();
    }

    public void setApiKeyActive(long id, boolean active) {
        DecisionApiKey key = em.find(DecisionApiKey.class, id);
        if (key == null) throw new IllegalArgumentException("Unknown API key.");
        key.setActive(active);
    }

    // ---------------------------------------------------------------- codec helpers

    public static String encodeScopes(Collection<String> scopes) { return String.join(",", scopes); }

    public static Set<String> decodeScopes(String value) {
        if (value == null || value.isBlank()) return Collections.emptySet();
        return new LinkedHashSet<>(Arrays.asList(value.split(",")));
    }

    public static String encodeLimits(Map<String, String> limits) {
        StringBuilder sb = new StringBuilder();
        limits.forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        return sb.toString();
    }

    public static Map<String, String> decodeLimits(String value) {
        Map<String, String> limits = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return limits;
        for (String pair : value.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) limits.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return limits;
    }

    private static long toEpochSeconds(LocalDateTime time) {
        return time.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
