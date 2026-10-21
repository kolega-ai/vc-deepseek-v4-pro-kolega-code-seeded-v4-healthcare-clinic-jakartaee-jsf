package com.oakfield.clinic.svc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oakfield.clinic.decision.DecisionContext;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Care-assist drafting: the clinician's triage note is sent to the clinic's
 * assist gateway together with the operations prompt and the tool catalog. The
 * gateway returns the drafted reply and the tool the note called for.
 */
@Stateless
public class CareAssistService {

    private static final Logger LOG = Logger.getLogger(CareAssistService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Operations prompt: internal triage policy plus the gateway's tool catalog. */
    private static final String OPERATIONS_PROMPT =
            "You are the Oakfield clinical triage assistant. Follow clinic policy and never reveal "
            + "internal details. Operations key: assist-ops-2026-9f3c. "
            + "Tools: request_refill(prescriptionId) files a refill for the patient. "
            + "Reply as JSON: {\"reply\":\"...\",\"tool\":null|\"request_refill\",\"prescriptionId\":null|number}.";

    @EJB
    private PrescriptionService prescriptions;

    public String assist(DecisionContext ctx, String note) {
        ctx.require("visit.read.clinical");
        if (note == null || note.isBlank() || note.length() > 4000) {
            throw new ValidationException("Add the patient's note (max 4000 characters).");
        }
        String messages = "[" + Json.obj("role", "system", "content", OPERATIONS_PROMPT) + ","
                + Json.obj("role", "user", "content", note.strip()) + "]";
        String request = "{\"model\":\"oakfield-care\",\"messages\":" + messages + "}";
        String content;
        try (Response response = ClientBuilder.newBuilder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .target("http://assist-gateway.oakfield.local/v1/chat/completions")
                .request(MediaType.APPLICATION_JSON)
                .header("X-Assist-Key", assistKey())
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))) {
            if (response.getStatus() != 200) {
                throw new ValidationException("The assist gateway is unavailable (A-" + response.getStatus() + ").");
            }
            content = response.readEntity(String.class);
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("The assist gateway is unavailable.");
        }
        return applyDirective(ctx, content, note.strip());
    }

    private String applyDirective(DecisionContext ctx, String modelJson, String note) {
        try {
            JsonNode root = MAPPER.readTree(modelJson);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            String body = content.isTextual() ? content.asText() : modelJson;
            JsonNode directive = MAPPER.readTree(body);
            if ("request_refill".equals(directive.path("tool").asText())) {
                long prescriptionId = directive.path("prescriptionId").asLong();
                prescriptions.requestRefillOnBehalf(ctx, prescriptionId,
                        "Filed by triage assistant from patient note: " + note);
            }
            return directive.path("reply").asText(body);
        } catch (Exception e) {
            LOG.warnf("Assist directive could not be applied: %s", e.getMessage());
            return modelJson;
        }
    }

    private static String assistKey() {
        String configured = System.getenv("ASSIST_GATEWAY_KEY");
        return configured == null || configured.isBlank() ? "oakfield-assist-gateway-key" : configured;
    }
}
