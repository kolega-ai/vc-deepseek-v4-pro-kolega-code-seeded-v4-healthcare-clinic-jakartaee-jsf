package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.util.Base64;

/**
 * Clinical document intake from the lab / referral bridge: results and
 * referral letters arrive as a JSON envelope with the file base64-encoded and
 * are filed straight into the patient's chart.
 */
@Path("/v1/documents/intake")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentIntakeResource {

    @Inject
    private DocumentService documents;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response intake(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        long patientId;
        String title, mediaType, fileName;
        byte[] bytes;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            patientId = o.getJsonNumber("patientId").longValue();
            title = o.getString("title", null);
            mediaType = o.getString("mediaType", null);
            fileName = o.getString("fileName", null);
            bytes = Base64.getDecoder().decode(o.getString("content"));
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with patientId, title, mediaType, fileName, content (base64).");
        }
        Document d = documents.intakeClinical(ctx, patientId, title, mediaType, fileName, bytes);
        return Response.status(Response.Status.CREATED)
                .entity("{\"documentId\":" + d.getId()
                        + ",\"storedAs\":\"" + JsonEsc.escape(d.getStoredName()) + "\""
                        + ",\"contentType\":\"" + JsonEsc.escape(d.getContentType()) + "\""
                        + ",\"status\":\"" + d.getStatus().name() + "\"}")
                .build();
    }

    /**
     * Render a filed document to a flat preview. The lab bridge selects the
     * converter profile that matches the scanner that produced the file.
     */
    @GET
    @Path("/{documentId}/render")
    public Response render(@PathParam("documentId") long documentId,
                           @HeaderParam("X-Render-Profile") String renderProfile,
                           @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        java.nio.file.Path preview = documents.renderPreview(ctx, documentId, renderProfile);
        return Response.ok("{\"preview\":\"" + JsonEsc.escape(preview.getFileName().toString()) + "\"}")
                .build();
    }
}
