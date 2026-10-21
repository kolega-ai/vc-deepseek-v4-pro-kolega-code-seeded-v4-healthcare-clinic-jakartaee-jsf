package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.AppointmentTypeService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON endpoint for appointment availability / external scheduling
 * integration. Authenticated with an API key (scope availability.read)
 * or a user session.
 */
@Path("/v1/availability")
@Produces(MediaType.APPLICATION_JSON)
public class AvailabilityResource {

    @Inject
    private AppointmentService appointments;

    @Inject
    private AppointmentTypeService types;

    @GET
    public Response availability(@QueryParam("providerId") long providerId,
                                 @QueryParam("date") String date,
                                 @QueryParam("typeId") long typeId,
                                 @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        if (!ctx.hasScope("availability.read") && !ctx.hasScope("patient.read") && !ctx.hasScope("patient.read.own")) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Scope availability.read is required.");
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (Exception e) {
            throw new ValidationException("Date must be yyyy-MM-dd.");
        }
        AppointmentType type = types.get(typeId);
        if (!type.isActive()) {
            throw new ValidationException("Appointment type is not active.");
        }
        List<LocalDateTime> slots = appointments.availableSlots(ctx, providerId, day, typeId);
        StringBuilder sb = new StringBuilder("{\"providerId\":").append(providerId)
                .append(",\"date\":\"").append(day)
                .append("\",\"typeId\":").append(typeId)
                .append(",\"type\":\"").append(JsonEsc.escape(type.getName()))
                .append("\",\"durationMinutes\":").append(type.getDurationMinutes())
                .append(",\"slots\":[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(slots.get(i)).append('"');
        }
        return Response.ok(sb.append("]}").toString()).build();
    }
}
