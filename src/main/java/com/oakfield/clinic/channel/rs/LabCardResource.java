package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.LabCardCodec;
import com.oakfield.clinic.svc.DocumentService;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Lab result card: the patient opens the "results are ready" card link, which
 * carries the result reference on the device, and the reported item is opened.
 */
@Path("/v1/lab/card")
@Produces(MediaType.APPLICATION_JSON)
public class LabCardResource {

    @Inject
    private DocumentService documents;

    /** Hand a released result's card link to the patient's app so it can be revisited. */
    @GET
    @Path("/code/{documentId}")
    public Response cardCode(@PathParam("documentId") long documentId,
                             @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        DocumentService.DownloadInfo info = documents.downloadInfo(ctx, documentId);
        return Response.ok("{\"documentId\":" + info.document().getId()
                        + ",\"card\":\"" + LabCardCodec.encode(documentId) + "\"}")
                .build();
    }

    /** Open a result from the card carried on the device. */
    @GET
    public Response open(@CookieParam("OF_LAB_CARD") String card,
                         @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        long resultId;
        try {
            resultId = LabCardCodec.decode(card);
        } catch (javax.crypto.BadPaddingException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"card_expired\"}").build();
        } catch (Exception e) {
            return Response.status(422)
                    .entity("{\"error\":\"card_format\"}").build();
        }
        DocumentService.DownloadInfo info = documents.downloadInfo(ctx, resultId);
        return Response.ok("{\"documentId\":" + info.document().getId()
                        + ",\"title\":\"" + JsonEsc.escape(info.document().getTitle()) + "\"}")
                .build();
    }
}
