package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.TokenCodec;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.svc.AppointmentTypeService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public reference data (appointment types and their seeded USD fees) for
 * integrations.
 *
 * PRECISION BASELINE (non-vulnerability, keep): an MD5 hash used only as an
 * ETag for this reference-data response. Non-cryptographic use - MD5 is NOT
 * used for any security purpose here, only as a cheap content fingerprint.
 */
@Path("/v1/reference")
@Produces(MediaType.APPLICATION_JSON)
public class ReferenceResource {

    @Inject
    private AppointmentTypeService types;

    @GET
    @Path("/appointment-types")
    public Response appointmentTypes(@Context Request request) {
        String body = buildBody(types.listActive());
        String etag = TokenCodec.md5Hex(body); // non-cryptographic: ETag only
        EntityTag tag = new EntityTag(etag);
        Response.ResponseBuilder notModified = request.evaluatePreconditions(tag);
        if (notModified != null) {
            return notModified.build();
        }
        return Response.ok(body).tag(tag).build();
    }

    private static String buildBody(List<AppointmentType> all) {
        StringBuilder sb = new StringBuilder("{\"types\":[");
        for (int i = 0; i < all.size(); i++) {
            AppointmentType t = all.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(t.getId())
              .append(",\"code\":\"").append(t.getCode())
              .append("\",\"name\":\"").append(JsonEsc.escape(t.getName()))
              .append("\",\"description\":\"").append(JsonEsc.escape(t.getDescription()))
              .append("\",\"durationMinutes\":").append(t.getDurationMinutes())
              .append(",\"feeAmount\":\"").append(fee(t))
              .append("\",\"feeCurrency\":\"").append(t.getFeeCurrency()).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String fee(AppointmentType t) {
        BigDecimal fee = t.getFeeAmount();
        return fee == null ? "0.00" : fee.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
