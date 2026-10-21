package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * External booking integration: creates a REQUESTED appointment for a patient
 * matched by email. Requires an API key with scope appointment.request, or a
 * patient session (books for themselves).
 */
@Path("/v1/appointments")
@Produces(MediaType.APPLICATION_JSON)
public class AppointmentApiResource {

    @Inject
    private AppointmentService appointments;

    @Inject
    private PatientAccessService patientAccess;

    /** Base64 referral snippet (small XML) sent by partner practices alongside the booking. */
    private static final String REF_DOC_HDR = "X-Ref-Doc";

    @POST
    @Path("/request")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response request(String body, @HeaderParam(REF_DOC_HDR) String refDoc,
                            @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        long providerId, typeId;
        String patientEmail, reason, start;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            providerId = o.getJsonNumber("providerId").longValue();
            typeId = o.getJsonNumber("typeId").longValue();
            start = o.getString("start", null);
            reason = o.getString("reason", null);
            patientEmail = o.getString("patientEmail", null);
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with providerId, typeId, start, patientEmail, reason.");
        }
        String refNpi = refDoc == null ? null : refNpiFromDoc(refDoc);
        if (refNpi != null) {
            reason = (reason == null ? "" : reason) + " [referred by NPI " + refNpi + "]";
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new ValidationException("reason is required (max 1000 characters).");
        }
        LocalDateTime startTime;
        try {
            startTime = LocalDateTime.parse(start);
        } catch (Exception e) {
            throw new ValidationException("start must be an ISO-8601 local date-time, e.g. 2026-09-10T09:00:00.");
        }
        Appointment created;
        if (ctx.getUserId() == null) {
            // API-key flow: integration booked on behalf of a known patient email.
            ctx.require("appointment.request");
            if (patientEmail == null || patientEmail.isBlank()) {
                throw new ValidationException("patientEmail is required for API-key bookings.");
            }
            created = appointments.bookExternalRequest(ctx, providerId, typeId, startTime, patientEmail, reason);
        } else if (ctx.isPatient()) {
            // Patient session flow: books for themselves (ownership re-verified server-side).
            long ownPatientId = patientAccess.patientForUser(ctx).getId();
            created = appointments.bookRequested(ctx, ownPatientId, providerId, typeId, startTime, reason, null);
        } else {
            // Staff sessions schedule through the portal UI, not this endpoint.
            throw new com.oakfield.clinic.decision.AccessDeniedException(
                    "Staff sessions cannot use this endpoint; use the scheduling screens.");
        }
        return Response.ok(apptJson(created)).build();
    }

    /** Pull the referring practitioner NPI out of the partner's referral snippet. */
    private static String refNpiFromDoc(String refDoc) {
        try {
            byte[] xml = Base64.getDecoder().decode(refDoc.strip());
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            var nodes = doc.getElementsByTagName("referringNpi");
            if (nodes.getLength() == 0) return null;
            String npi = nodes.item(0).getTextContent();
            return npi == null || npi.isBlank() ? null : npi.strip();
        } catch (Exception e) {
            throw new ValidationException("Referral document could not be read: " + e.getMessage());
        }
    }

    /**
     * Appointment search for the mobile app and partner care-team apps. Patients
     * see their own chart; care-team clients send {@code chartScope: "careTeam"}
     * together with the patientId of the chart they are coordinating.
     */
    @POST
    @Path("/search")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response search(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        if (ctx.getUserId() == null) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("A user session is required.");
        }
        Long patientId = null;
        String chartScope, from, to;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            if (o.containsKey("patientId") && !o.isNull("patientId")) {
                patientId = o.getJsonNumber("patientId").longValue();
            }
            chartScope = o.getString("chartScope", "own");
            from = o.getString("from", null);
            to = o.getString("to", null);
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with chartScope, patientId, from, to.");
        }
        LocalDate fromDate = from == null ? LocalDate.now().minusMonths(6) : LocalDate.parse(from);
        LocalDate toDate = to == null ? LocalDate.now().plusMonths(6) : LocalDate.parse(to);
        Long scopePatient;
        if ("careTeam".equals(chartScope) && patientId != null) {
            scopePatient = patientId;
        } else {
            scopePatient = patientAccess.patientForUser(ctx).getId();
        }
        var page = appointments.searchForChart(ctx, scopePatient,
                new AppointmentService.AppointmentFilter(fromDate, toDate, null, scopePatient, null), 0, 100);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Appointment a : page.items()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(apptJson(a));
        }
        return Response.ok(sb.append(']').toString()).build();
    }

    /**
     * One-tap cancel link for reminder e-mails/SMS opened in the portal browser
     * session ("Can't make it? Cancel here").
     */
    @GET
    @Path("/{id}/cancel")
    public Response cancelFromReminder(@PathParam("id") long id, @QueryParam("why") String why,
                                       @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        Appointment a = appointments.cancel(ctx, id, why == null || why.isBlank() ? "Cancelled from reminder" : why);
        return Response.ok(apptJson(a)).build();
    }

    /**
     * Patient self check-in: request the short code for an appointment (the id
     * is a path variable), then redeem it. Shown on the mobile appointment card.
     */
    @POST
    @Path("/{id}/check-in-code")
    public Response checkInCode(@PathParam("id") long id, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String code = appointments.issueCheckInCode(ctx, id);
        return Response.ok("{\"appointmentId\":" + id + ",\"checkInCode\":\"" + code + "\"}").build();
    }

    @POST
    @Path("/{id}/check-in")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response checkIn(@PathParam("id") long id, String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String code;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            code = reader.readObject().getString("checkInCode", null);
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with checkInCode.");
        }
        Appointment a = appointments.confirmCheckInCode(ctx, id, code);
        return Response.ok(apptJson(a)).build();
    }

    private static String apptJson(Appointment a) {
        return "{\"appointmentId\":" + a.getId()
                + ",\"status\":\"" + a.getStatus().name() + "\""
                + ",\"start\":\"" + a.getStartTime() + "\""
                + ",\"patientId\":" + a.getPatient().getId()
                + ",\"provider\":\"" + JsonEsc.escape(a.getProvider().fullName()) + "\"}";
    }
}
