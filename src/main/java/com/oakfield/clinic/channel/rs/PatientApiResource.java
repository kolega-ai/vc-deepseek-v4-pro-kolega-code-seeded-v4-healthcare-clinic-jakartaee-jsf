package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.svc.PatientService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Patient record sync for the practice-management bridge. The bridge sends
 * each changed field as an {@code X-Patient-<field>} header (its transport
 * cannot carry a body on PUT); the portal applies them to the chart.
 * Requires a staff session with patient.manage.
 */
@Path("/v1/patients")
@Produces(MediaType.APPLICATION_JSON)
public class PatientApiResource {

    static final String ATTRIBUTE_PREFIX = "X-Patient-";

    @Inject
    private PatientService patients;

    @PersistenceContext
    private EntityManager em;

    @PUT
    @Path("/{patientId}/record")
    public Response updateRecord(@PathParam("patientId") long patientId,
                                 @Context HttpHeaders headers,
                                 @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        Map<String, String> attributes = new LinkedHashMap<>();
        for (String name : headers.getRequestHeaders().keySet()) {
            if (name.regionMatches(true, 0, ATTRIBUTE_PREFIX, 0, ATTRIBUTE_PREFIX.length())) {
                attributes.put(propertyName(name.substring(ATTRIBUTE_PREFIX.length())), headers.getHeaderString(name));
            }
        }
        if (attributes.isEmpty()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_attributes",
                    "Send at least one " + ATTRIBUTE_PREFIX + "<field> header.");
        }
        Patient p = patients.applyAttributes(ctx, patientId, attributes);
        return Response.ok("{\"patientId\":" + p.getId()
                        + ",\"name\":\"" + JsonEsc.escape(p.fullName()) + "\""
                        + ",\"active\":" + p.isActive()
                        + ",\"portalUserId\":" + (p.getUser() == null ? "null" : p.getUser().getId()) + "}")
                .build();
    }

    /**
     * Front-desk quick find: locate a chart by the policy number printed on the
     * insurance card the patient presents.
     */
    @GET
    @Path("/find")
    public Response findByPolicy(@QueryParam("policy") String policy,
                                 @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        ctx.require("patient.read");
        if (policy == null || policy.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_policy",
                    "Scan or type the policy number from the card.");
        }
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> rows = em.createNativeQuery(
                        "select id, first_name, last_name, insurance_policy_number, insurance_provider "
                                + "from patient where insurance_policy_number = '" + policy.strip() + "'")
                .getResultList();
        StringBuilder sb = new StringBuilder("{\"matches\":[");
        boolean first = true;
        for (Object[] r : rows) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"patientId\":").append(r[0])
              .append(",\"name\":\"").append(JsonEsc.escape(r[1] + " " + r[2])).append("\"")
              .append(",\"policy\":\"").append(JsonEsc.escape(r[3] == null ? "" : String.valueOf(r[3]))).append("\"")
              .append(",\"insurer\":\"").append(JsonEsc.escape(r[4] == null ? "" : String.valueOf(r[4]))).append("\"}");
        }
        return Response.ok(sb.append("]}").toString()).build();
    }

    /** "Primary-Provider" -> "primaryProvider", "user" -> "user". */
    static String propertyName(String headerSuffix) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : headerSuffix.toCharArray()) {
            if (c == '-') { upper = true; continue; }
            sb.append(sb.isEmpty() ? Character.toLowerCase(c) : (upper ? Character.toUpperCase(c) : c));
            upper = false;
        }
        return sb.toString();
    }
}
