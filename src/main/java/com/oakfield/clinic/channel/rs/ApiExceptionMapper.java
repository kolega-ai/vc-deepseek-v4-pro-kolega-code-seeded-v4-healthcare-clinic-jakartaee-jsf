package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.svc.NotFoundException;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Maps domain exceptions to compact JSON error responses (no stack traces leak). */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class);

    @Override
    public Response toResponse(Throwable t) {
        // EJB boundaries wrap business exceptions; unwrap to the meaningful cause.
        Throwable cause = t;
        while ((cause instanceof jakarta.ejb.EJBException || cause instanceof jakarta.ejb.EJBAccessException)
                && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof AccessDeniedException e) {
            return ApiAuthFilter.json(Response.Status.FORBIDDEN, "forbidden", e.getMessage());
        }
        if (cause instanceof NotAuthenticatedException e) {
            return ApiAuthFilter.json(Response.Status.UNAUTHORIZED, "authentication_required", e.getMessage());
        }
        if (cause instanceof ValidationException e) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "validation_error", e.getMessage());
        }
        if (cause instanceof NotFoundException e) {
            return ApiAuthFilter.json(Response.Status.NOT_FOUND, "not_found", e.getMessage());
        }
        LOG.errorf("Unhandled API error: %s", t.toString());
        return ApiAuthFilter.json(Response.Status.INTERNAL_SERVER_ERROR, "internal_error",
                "Something went wrong. Please try again.");
    }
}
