package com.oakfield.clinic.channel.security;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import jakarta.enterprise.inject.spi.CDI;

import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Channel-side session plumbing: resolves the opaque signed session token from
 * the cookie and asks the decision tier to authorize the request. The resolved
 * DecisionContext is stored as a request attribute; JAX-RS resources and JSF
 * backing beans use it - they never build one from client-supplied identity.
 */
@WebFilter(urlPatterns = {"/app/*", "/api/*"})
public class DecisionContextFilter implements Filter {

    public static final String CTX_ATTR = "oakfield.decisionContext";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String requestId = requestId();
        req.setAttribute("oakfield.requestId", requestId);
        // The authentication mechanism may already have resolved the decision
        // context for this request (identity establishment from the cookie).
        String token = SessionTokens.get(req);
        if (token != null && req.getAttribute(CTX_ATTR) == null) {
            try {
                DecisionContext ctx = CDI.current().select(com.oakfield.clinic.decision.DecisionService.class).get()
                        .resolve(token, clientIp(req), requestId);
                req.setAttribute(CTX_ATTR, ctx);
            } catch (NotAuthenticatedException | IllegalStateException e) {
                // Anonymous for this request; protected paths reject it downstream.
            }
        }
        // API surface: Bearer session tokens and API keys (decision-tier resolution).
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.startsWith("/api/v1/") && req.getAttribute(CTX_ATTR) == null) {
            resolveApiCredentials(req, path);
        }
        chain.doFilter(request, response);
    }

    /**
     * Resolve API credentials (Authorization: Bearer session token, or
     * X-Api-Key). The JAX-RS resources read the result from the request
     * attribute; failures are deferred to the resource layer so it can answer
     * with the right status code.
     */
    private void resolveApiCredentials(HttpServletRequest req, String path) {
        if (path.equals("/api/v1/auth/login")) return;
        com.oakfield.clinic.decision.DecisionService decision =
                CDI.current().select(com.oakfield.clinic.decision.DecisionService.class).get();
        String bearer = bearer(req);
        if (bearer != null) {
            try {
                DecisionContext ctx = decision.resolve(bearer, clientIp(req), requestIdOf(req));
                req.setAttribute(CTX_ATTR, ctx);
            } catch (NotAuthenticatedException e) {
                req.setAttribute("oakfield.api.authError", "invalid_session");
            }
            return;
        }
        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            var info = decision.resolveApiKey(apiKey.strip());
            if (info.isPresent()) {
                com.oakfield.clinic.decision.DecisionService.ApiKeyInfo key = info.get();
                DecisionContext ctx = new DecisionContext(null, "apikey:" + key.keyPrefix(), key.name(), "API_KEY",
                        key.scopes(), java.util.Map.of(), 0, null, clientIp(req), requestIdOf(req));
                req.setAttribute(CTX_ATTR, ctx);
            } else {
                req.setAttribute("oakfield.api.authError", "invalid_api_key");
            }
            return;
        }
        req.setAttribute("oakfield.api.authError", "authentication_required");
    }

    private static String bearer(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (header == null) return null;
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).strip();
        }
        return null;
    }

    public static DecisionContext ctxOf(HttpServletRequest req) {
        Object ctx = req.getAttribute(CTX_ATTR);
        if (ctx instanceof DecisionContext dc) return dc;
        throw new NotAuthenticatedException("No authenticated session.");
    }

    public static DecisionContext ctxOrNull(HttpServletRequest req) {
        Object ctx = req.getAttribute(CTX_ATTR);
        return ctx instanceof DecisionContext dc ? dc : null;
    }

    public static String requestIdOf(HttpServletRequest req) {
        Object id = req.getAttribute("oakfield.requestId");
        return id == null ? "n/a" : id.toString();
    }

    public static String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        return req.getRemoteAddr();
    }

    private static String requestId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(16);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
