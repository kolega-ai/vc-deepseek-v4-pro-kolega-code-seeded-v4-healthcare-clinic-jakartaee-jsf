package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.UserAdminService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.StringReader;

/**
 * Staffing integration intake: the clinic's staffing vendor posts a provisioning
 * message with the new hire's portal login identifier and the passcode it
 * issued, and the portal opens the account.
 */
@Path("/v1/staffing")
@Produces(MediaType.APPLICATION_JSON)
public class StaffingMessageResource {

    @Inject
    private UserAdminService users;

    @POST
    @Path("/message")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response message(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String email, fullName, passcode;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            email = o.getString("email", null);
            fullName = o.getString("fullName", null);
            passcode = o.getString("passcode", null);
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with email, fullName, passcode.");
        }
        User user = users.provisionFromMessage(ctx,
                new UserAdminService.StaffProvision(email, fullName, passcode));
        return Response.status(Response.Status.CREATED)
                .entity("{\"userId\":" + user.getId() + ",\"email\":\"" + JsonEsc.escape(user.getEmail()) + "\"}")
                .build();
    }

    /**
     * Separation notice: the staffing vendor reports that an engagement ended,
     * and the portal closes the account.
     */
    @POST
    @Path("/separation")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response separation(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        String email;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            email = reader.readObject().getString("email", null);
        } catch (Exception e) {
            throw new ValidationException("Send a JSON object with email.");
        }
        User user = users.deactivateFromMessage(ctx, email);
        return Response.ok("{\"userId\":" + user.getId()
                        + ",\"status\":\"" + user.getStatus().name() + "\"}")
                .build();
    }
}
