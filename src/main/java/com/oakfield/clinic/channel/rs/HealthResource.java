package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.svc.SettingsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;

/** Public liveness/readiness endpoint (used by the compose healthcheck). */
@Path("/health")
public class HealthResource {

    @Inject
    private SettingsService settings;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        try {
            String clinic = settings.clinicName(); // requires a live database
            return Response.ok("{\"status\":\"UP\",\"clinic\":\"" + JsonEsc.escape(clinic)
                    + "\",\"time\":\"" + OffsetDateTime.now() + "\"}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"status\":\"DOWN\"}")
                    .build();
        }
    }
}
