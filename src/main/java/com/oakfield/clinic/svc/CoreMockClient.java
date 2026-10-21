package com.oakfield.clinic.svc;

import jakarta.ejb.Stateless;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDate;

/**
 * Client for the vendor-core mock (pharmacy clearinghouse / payer stub).
 * core-mock runs as its own network endpoint (/core/...) with its own
 * credential (X-Core-Key) and its own error codes (CORE-xxx). Every call is
 * authenticated with a scope token minted by the decision tier; core-mock
 * verifies the signature and enforces the scope claim - raw parameters are
 * never trusted.
 */
@Stateless
public class CoreMockClient {

    private static final Logger LOG = Logger.getLogger(CoreMockClient.class);

    @jakarta.ejb.EJB
    private com.oakfield.clinic.decision.DecisionService decision;

    @jakarta.ejb.EJB
    private SettingsService settings;

    public record EligibilityResult(String status, String planName, String copayAmount, String currency,
                                    String message) {
        public boolean ok() { return "OK".equals(status); }
    }

    public record TransmitResult(String status, String reference, String message) {
        public boolean ok() { return "OK".equals(status); }
    }

    /** Payer eligibility check for a patient (invoked from chart / registration flows). */
    public EligibilityResult checkEligibility(long patientId, LocalDate dateOfBirth, String insuranceProvider,
                                              String policyNumber) {
        String scopeToken = decision.mintCoreToken("eligibility.read", String.valueOf(patientId));
        try (Response response = client().target(settings.baseUrl() + "/core/eligibility/check")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + scopeToken)
                .header("X-Core-Key", decision.coreMockKey())
                .post(Entity.entity(Json.obj(
                        "patientId", String.valueOf(patientId),
                        "dateOfBirth", String.valueOf(dateOfBirth),
                        "insuranceProvider", insuranceProvider == null ? "" : insuranceProvider,
                        "policyNumber", policyNumber == null ? "" : policyNumber), MediaType.APPLICATION_JSON_TYPE))) {
            String body = response.readEntity(String.class);
            if (response.getStatus() != 200) {
                LOG.warnf("core-mock eligibility returned %d: %s", response.getStatus(), body);
                return new EligibilityResult("ERROR", null, null, null, "Clearinghouse unavailable (CORE-" + response.getStatus() + ").");
            }
            return parseEligibility(body);
        } catch (Exception e) {
            // The vendor core is an external system: failures must not break callers.
            LOG.warnf("core-mock eligibility call failed: %s", e.getMessage());
            return new EligibilityResult("ERROR", null, null, null, "Clearinghouse unavailable.");
        }
    }

    /** Transmit an approved refill to the pharmacy clearinghouse. */
    public TransmitResult transmitRefill(long prescriptionId, long patientId, String patientName,
                                         String medication, String dosage, String providerNpi) {
        String scopeToken = decision.mintCoreToken("refill.transmit", String.valueOf(prescriptionId));
        try (Response response = client().target(settings.baseUrl() + "/core/refills/transmit")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + scopeToken)
                .header("X-Core-Key", decision.coreMockKey())
                .post(Entity.entity(Json.obj(
                        "prescriptionId", String.valueOf(prescriptionId),
                        "patientId", String.valueOf(patientId),
                        "patientName", patientName,
                        "medication", medication,
                        "dosage", dosage,
                        "providerNpi", providerNpi), MediaType.APPLICATION_JSON_TYPE))) {
            String body = response.readEntity(String.class);
            if (response.getStatus() != 200) {
                LOG.warnf("core-mock refill transmit returned %d: %s", response.getStatus(), body);
                return new TransmitResult("ERROR", null, "Clearinghouse rejected the transmission (CORE-" + response.getStatus() + ").");
            }
            return parseTransmit(body);
        } catch (Exception e) {
            // Transient failures are recorded as FAILED and retried by the background job.
            LOG.warnf("core-mock refill transmit failed: %s", e.getMessage());
            return new TransmitResult("ERROR", null, "Clearinghouse unreachable.");
        }
    }

    private static EligibilityResult parseEligibility(String json) {
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new java.io.StringReader(json))) {
            jakarta.json.JsonObject o = reader.readObject();
            return new EligibilityResult(
                    o.getString("status", "ERROR"),
                    o.getString("planName", null),
                    o.getString("copay", null),
                    o.getString("currency", "USD"),
                    o.getString("message", null));
        } catch (Exception e) {
            return new EligibilityResult("ERROR", null, null, null, "Clearinghouse returned an unreadable response.");
        }
    }

    private static TransmitResult parseTransmit(String json) {
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new java.io.StringReader(json))) {
            jakarta.json.JsonObject o = reader.readObject();
            return new TransmitResult(o.getString("status", "ERROR"),
                    o.getString("reference", null),
                    o.getString("message", null));
        } catch (Exception e) {
            return new TransmitResult("ERROR", null, "Clearinghouse returned an unreadable response.");
        }
    }

    private static jakarta.ws.rs.client.Client client() {
        return ClientBuilder.newBuilder().connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build();
    }
}
