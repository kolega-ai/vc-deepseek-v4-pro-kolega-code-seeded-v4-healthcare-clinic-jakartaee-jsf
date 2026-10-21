package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.decision.NotAuthenticatedException;
import com.oakfield.clinic.decision.TokenCodec;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.Prescription;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Vendor-core mock: pharmacy clearinghouse / payer stub. Own network endpoint
 * (/core/...), own credential (X-Core-Key), own error codes (CORE-xxx).
 * Every call must present a scope token minted by the decision tier; this
 * resource verifies the signature and enforces the scope claim. Raw scope
 * parameters in the body are ignored.
 */
@Path("/")
@Stateless
public class CoreMockResource {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private DecisionService decision;

    @POST
    @Path("/eligibility/check")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response eligibility(String body, @Context HttpHeaders headers) {
        Response authError = checkCredentials(headers, "eligibility.read");
        if (authError != null) return authError;
        long patientId;
        String insuranceProvider;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            patientId = parseLong(o, "patientId");
            insuranceProvider = o.getString("insuranceProvider", null);
        } catch (Exception e) {
            return coreError(Response.Status.BAD_REQUEST, "CORE-400", "Malformed request body.");
        }
        String subject = subjectOf(headers);
        if (!String.valueOf(patientId).equals(subject)) {
            return coreError(Response.Status.FORBIDDEN, "CORE-403", "Scope token does not cover this subject.");
        }
        Patient patient = em.find(Patient.class, patientId);
        if (patient == null) {
            return coreError(Response.Status.NOT_FOUND, "CORE-404", "Patient not found in the clearinghouse.");
        }
        String insurer = insuranceProvider == null ? "" : insuranceProvider;
        String plan;
        String copay;
        if (insurer.contains("Medicare")) {
            plan = "Medicare Demo Plan";
            copay = "0.00";
        } else if (insurer.contains("Northgate")) {
            plan = "Northgate PPO Gold";
            copay = "25.00";
        } else if (insurer.contains("Blue Ridge")) {
            plan = "Blue Ridge HMO Standard";
            copay = "15.00";
        } else if (insurer.isBlank()) {
            plan = "Self-pay";
            copay = "95.00";
        } else if (insurer.contains("Pending")) {
            return coreError(Response.Status.CONFLICT, "CORE-409", "Eligibility pending review by the payer.");
        } else {
            plan = "Unlisted plan";
            copay = "50.00";
        }
        return Response.ok("{\"status\":\"OK\",\"planName\":\"" + JsonEsc.escape(plan)
                + "\",\"copay\":\"" + copay + "\",\"currency\":\"USD\","
                + "\"message\":\"Eligibility confirmed for " + JsonEsc.escape(plan) + ".\"}")
                .build();
    }

    @POST
    @Path("/refills/transmit")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response transmit(String body, @Context HttpHeaders headers) {
        Response authError = checkCredentials(headers, "refill.transmit");
        if (authError != null) return authError;
        long prescriptionId;
        String medication = "";
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            prescriptionId = parseLong(o, "prescriptionId");
            medication = o.getString("medication", "");
        } catch (Exception e) {
            return coreError(Response.Status.BAD_REQUEST, "CORE-400", "Malformed request body.");
        }
        String subject = subjectOf(headers);
        if (!String.valueOf(prescriptionId).equals(subject)) {
            return coreError(Response.Status.FORBIDDEN, "CORE-403", "Scope token does not cover this subject.");
        }
        Prescription rx = em.find(Prescription.class, prescriptionId);
        if (rx == null) {
            return coreError(Response.Status.NOT_FOUND, "CORE-404", "Prescription not found in the clearinghouse.");
        }
        if (medication.startsWith("Reject") || medication.startsWith("REJECT")) {
            return coreError(Response.Status.PAYMENT_REQUIRED, "CORE-402",
                    "Prior authorization required for this medication.");
        }
        String reference = "PHA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return Response.ok("{\"status\":\"OK\",\"reference\":\"" + reference
                + "\",\"message\":\"Transmitted to the pharmacy network.\"}").build();
    }

    /** Core-mock's own credential + signed scope token. Never trusts raw scope params. */
    private Response checkCredentials(HttpHeaders headers, String requiredScope) {
        String key = headers.getHeaderString("X-Core-Key");
        if (key == null || !constantTimeEquals(decision.coreMockKey(), key)) {
            return coreError(Response.Status.UNAUTHORIZED, "CORE-401", "Invalid core credential.");
        }
        String bearer = headers.getHeaderString("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            return coreError(Response.Status.UNAUTHORIZED, "CORE-401", "Missing scope token.");
        }
        TokenCodec.CoreTokenParts parts;
        try {
            parts = decision.verifyCoreToken(bearer.substring(7).strip());
        } catch (NotAuthenticatedException e) {
            return coreError(Response.Status.UNAUTHORIZED, "CORE-401", "Invalid or expired scope token.");
        }
        if (!requiredScope.equals(parts.scope())) {
            return coreError(Response.Status.FORBIDDEN, "CORE-403",
                    "Token scope '" + parts.scope() + "' does not permit this operation.");
        }
        return null;
    }

    private String subjectOf(HttpHeaders headers) {
        String bearer = headers.getHeaderString("Authorization");
        try {
            return decision.verifyCoreToken(bearer.substring(7).strip()).subject();
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(jakarta.json.JsonObject o, String key) {
        try {
            return o.getJsonNumber(key).longValue();
        } catch (Exception e) {
            return Long.parseLong(o.getString(key));
        }
    }

    private static Response coreError(Response.Status status, String code, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"status\":\"ERROR\",\"code\":\"" + code + "\",\"message\":\""
                        + JsonEsc.escape(message) + "\"}")
                .build();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
