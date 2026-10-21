package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.svc.CareAssistService;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Triage drafting helper for the clinician queue: the patient's note is
 * passed to the clinic assist gateway, which drafts the reply and files the
 * follow-up the note calls for.
 */
@Path("/v1/assist")
@Produces(MediaType.APPLICATION_JSON)
public class CareAssistResource {

    @Inject
    private CareAssistService assist;

    @GET
    @Path("/answer")
    public Response answer(@QueryParam("note") String note, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String reply = assist.assist(ctx, note);
        return Response.ok("{\"reply\":\"" + JsonEsc.escape(reply) + "\"}").build();
    }
}
