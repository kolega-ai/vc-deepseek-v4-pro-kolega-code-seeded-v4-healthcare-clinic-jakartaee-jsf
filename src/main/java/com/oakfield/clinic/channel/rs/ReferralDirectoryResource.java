package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.svc.ProviderService;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.TimeUnit;

/**
 * Referral network: when a clinician refers a patient to a specialist practice,
 * the portal pulls that practice's published practitioner directory so the
 * referral form can be filled from live data instead of a stale paper list.
 * Practices in the network publish it under a well-known path on their own host.
 */
@Path("/v1/referrals")
@Produces(MediaType.APPLICATION_JSON)
public class ReferralDirectoryResource {

    static final String DIRECTORY_PATH = "/.well-known/practitioner-directory.json";

    @Inject
    private ProviderService providers;

    /**
     * The referral workbench keeps the practitioner group it filtered on in a
     * cookie; this lists the practitioners in that group from the directory.
     */
    @GET
    @Path("/group")
    public Response practiceGroup(@CookieParam("OF_REF_GROUP") String group,
                                  @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("patient.read");
        if (group == null || group.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_group",
                    "Pick a practitioner group first.");
        }
        java.util.List<String> members = providers.practitionersInUnit(ctx, group);
        StringBuilder sb = new StringBuilder("{\"group\":\"").append(JsonEsc.escape(group)).append("\",\"members\":[");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(JsonEsc.escape(members.get(i))).append('"');
        }
        return Response.ok(sb.append("]}").toString()).build();
    }

    @GET
    @Path("/{practiceHost}/practitioners")
    public Response practitionerDirectory(@PathParam("practiceHost") String practiceHost,
                                          @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("patient.read");
        String directoryUrl = "https://" + practiceHost.strip() + DIRECTORY_PATH;
        Client client = ClientBuilder.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
        try (Response upstream = client.target(directoryUrl).request(MediaType.APPLICATION_JSON).get()) {
            String payload = upstream.readEntity(String.class);
            return Response.status(upstream.getStatus())
                    .entity("{\"practice\":\"" + JsonEsc.escape(practiceHost) + "\",\"directory\":"
                            + (payload == null || payload.isBlank() ? "null" : payload) + "}")
                    .build();
        } catch (Exception e) {
            return ApiAuthFilter.json(Response.Status.BAD_GATEWAY, "practice_unreachable",
                    "The practice directory could not be reached: " + e.getMessage());
        } finally {
            client.close();
        }
    }
}
