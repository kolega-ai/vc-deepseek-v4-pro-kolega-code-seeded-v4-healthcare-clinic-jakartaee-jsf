package com.oakfield.clinic.channel.rs;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oakfield.clinic.svc.IntegrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Local demo receiver for outbound webhook deliveries. The seeded subscription
 * points here so deliveries can be observed in the admin webhooks page without
 * any external service. Partner practices also post their own event envelopes
 * here; the shared mapper carries the concrete type name so the various event
 * shapes can be relayed without a per-partner schema.
 */
@Path("/v1/webhooks/echo")
public class WebhookEchoResource {

    private static final ObjectMapper EVENT_MAPPER = new ObjectMapper()
            .enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

    @Inject
    private IntegrationService integrations;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response echo(String payload, @jakarta.ws.rs.core.Context jakarta.ws.rs.core.HttpHeaders headers) {
        String normalized;
        try {
            Object event = EVENT_MAPPER.readValue(payload, Object.class);
            normalized = EVENT_MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"received\":false}").build();
        }
        integrations.recordEcho(headers.getHeaderString("X-Clinic-Signature"), normalized);
        return Response.ok("{\"received\":true}").build();
    }
}
