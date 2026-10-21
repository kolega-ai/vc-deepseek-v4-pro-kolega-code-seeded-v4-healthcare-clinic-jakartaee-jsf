package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * API credential plumbing. Resolution itself happens in the servlet
 * DecisionContextFilter (which covers every /api/* request): Bearer session
 * tokens and X-Api-Key values are resolved by the decision tier and stored as
 * a request attribute. This class exposes the typed accessor and the JSON
 * error envelope used by the JAX-RS resources and the exception mapper.
 */
public final class ApiAuthFilter {

    /** Same attribute the decision filter stores the resolved context under. */
    public static final String CTX_ATTR = "oakfield.decisionContext";
    public static final String AUTH_ERROR_ATTR = "oakfield.api.authError";

    private ApiAuthFilter() { }

    /** Read the decision context resolved for this API request. */
    public static DecisionContext ctx(HttpServletRequest request) {
        Object o = request.getAttribute(CTX_ATTR);
        if (o instanceof DecisionContext ctx) return ctx;
        Object err = request.getAttribute(AUTH_ERROR_ATTR);
        if ("invalid_session".equals(err)) {
            throw new NotAuthenticatedException("Session is missing, expired, or revoked.");
        }
        if ("invalid_api_key".equals(err)) {
            throw new NotAuthenticatedException("Unknown or inactive API key.");
        }
        throw new NotAuthenticatedException("Authentication required.");
    }

    public static Response json(Response.Status status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"" + code + "\",\"message\":\"" + JsonEsc.escape(message) + "\"}")
                .build();
    }
}
