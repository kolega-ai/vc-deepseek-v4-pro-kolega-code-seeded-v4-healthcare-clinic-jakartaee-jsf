package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.WebhookDelivery;
import com.oakfield.clinic.model.WebhookSubscription;
import com.oakfield.clinic.svc.IntegrationService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration settings API for the clinic-admin tooling: manage outbound webhook
 * subscriptions (subs) and replay recorded deliveries. Same rules as the admin
 * webhooks page; requires a session with integration.manage.
 */
@Path("/v1/webhooks/subs")
@Produces(MediaType.APPLICATION_JSON)
public class WebhookSubscriptionResource {

    @Inject
    private IntegrationService integrations;

    /** Current subscription config, as stored, so the ops tooling can diff it against its manifest. */
    @GET
    public Response listSubs(@Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("integration.manage");
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (WebhookSubscription w : integrations.listSubscriptions()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(subJson(w));
        }
        return Response.ok(sb.append(']').toString()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createSub(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String url, sec;
        List<String> evts = new ArrayList<>();
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            url = o.getString("url", null);
            sec = o.getString("sec", null);
            for (jakarta.json.JsonValue v : o.getJsonArray("evts")) {
                evts.add(((jakarta.json.JsonString) v).getString());
            }
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with url, sec and evts.");
        }
        WebhookSubscription w = integrations.createSubscription(ctx, url, sec, evts);
        return Response.status(Response.Status.CREATED).entity(subJson(w)).build();
    }

    /**
     * Replay one recorded delivery to another receiver host (partner moved its
     * endpoint). Path and payload are taken from the original delivery.
     */
    @POST
    @Path("/deliveries/{deliveryId}/forward/{targetHost}")
    public Response forwardDelivery(@PathParam("deliveryId") long deliveryId,
                                    @PathParam("targetHost") String targetHost,
                                    @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        boolean ok = integrations.forwardDelivery(ctx, deliveryId, targetHost);
        WebhookDelivery d = integrations.deliveryById(deliveryId);
        return Response.ok("{\"deliveryId\":" + deliveryId
                        + ",\"forwarded\":" + ok
                        + ",\"status\":\"" + d.getStatus().name() + "\""
                        + ",\"lastError\":\"" + JsonEsc.escape(d.getLastError()) + "\"}")
                .build();
    }

    private static String subJson(WebhookSubscription w) {
        return "{\"id\":" + w.getId()
                + ",\"url\":\"" + JsonEsc.escape(w.getUrl()) + "\""
                + ",\"sec\":\"" + JsonEsc.escape(w.getSecret()) + "\""
                + ",\"evts\":\"" + JsonEsc.escape(w.getEvents()) + "\""
                + ",\"active\":" + w.isActive() + "}";
    }
}
