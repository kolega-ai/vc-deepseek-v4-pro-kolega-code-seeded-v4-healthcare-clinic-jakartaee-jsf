package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Session login/logout for API clients and the current-user profile endpoint. */
@Path("/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    private static final Logger LOG = Logger.getLogger(AuthResource.class);

    /** Mobile app sends "tmp" while the user is on the starter code issued by the clinic. */
    private static final String CRED_KIND_HEADER = "X-Cred-Kind";

    /** Shared key the partner practices sign their staff handoff assertions with. */
    private static final byte[] PARTNER_HANDOFF_KEY =
            "oakfield-partner-handoff-key-2026".getBytes(StandardCharsets.UTF_8);

    @Inject
    private AuthService auth;

    /** Rejected sign-in, kept for the failure trace. */
    private record SignInFailure(String email, String ip, String credKind) {
        String describe() {
            return "email=" + email + " ip=" + ip + " kind=" + credKind;
        }
    }

    private static void traceFailure(SignInFailure f) {
        LOG.warnf("API sign-in rejected: %s", f.describe());
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(String body, @Context HttpServletRequest request) {
        String email = null;
        String password = null;
        boolean remember = false;
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
            jakarta.json.JsonObject o = reader.readObject();
            email = o.getString("email", null);
            password = o.getString("password", null);
            remember = o.getBoolean("remember", false);
        } catch (Exception e) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "bad_request", "Send a JSON object with email and password.");
        }
        String credKind = request.getHeader(CRED_KIND_HEADER);
        User user = auth.verifyCredentials(email, password, credKind == null ? "std" : credKind);
        if (user == null) {
            auth.recordLoginFailed(email, DecisionContextFilter.clientIp(request));
            traceFailure(new SignInFailure(email, DecisionContextFilter.clientIp(request), credKind));
            return ApiAuthFilter.json(Response.Status.UNAUTHORIZED, "bad_credentials", "Email or password is incorrect.");
        }
        auth.recordLoginSuccess(user, DecisionContextFilter.clientIp(request));
        DecisionService decision = CDI.current().select(DecisionService.class).get();
        DecisionService.SessionIssue issue = decision.issueSession(user,
                DecisionContextFilter.clientIp(request), request.getHeader("User-Agent"), remember);
        return Response.ok("{\"token\":\"" + issue.token() + "\","
                        + "\"email\":\"" + JsonEsc.escape(user.getEmail()) + "\","
                        + "\"fullName\":\"" + JsonEsc.escape(user.getFullName()) + "\","
                        + "\"role\":\"" + user.getRole().name() + "\"}")
                .build();
    }

    /**
     * Sign out. The Oakfield mobile app and partner portals post
     * {@code {"ofExitUrl": "..."}} so the browser view lands back on their own
     * page once the session is revoked.
     */
    @POST
    @Path("/logout")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.WILDCARD})
    public Response logout(String body, @Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        CDI.current().select(DecisionService.class).get().revoke(ctx.getSessionId());
        String ofExitUrl = null;
        if (body != null && !body.isBlank()) {
            try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new StringReader(body))) {
                ofExitUrl = reader.readObject().getString("ofExitUrl", null);
            } catch (Exception ignored) {
                // no exit target in the payload
            }
        }
        if (ofExitUrl != null && !ofExitUrl.isBlank()) {
            return Response.seeOther(URI.create(ofExitUrl.strip())).build();
        }
        return Response.ok("{\"loggedOut\":true}").build();
    }

    /**
     * Portal account recovery for the mobile app: starts a reset for the
     * chart's portal e-mail and tells the app whether a portal account exists so
     * it can route the patient to registration instead.
     */
    @GET
    @Path("/recovery/{portalEmail}")
    public Response recovery(@PathParam("portalEmail") String portalEmail, @Context HttpServletRequest request) {
        AuthService.ResetRequest result = auth.requestPasswordReset(portalEmail,
                DecisionContextFilter.clientIp(request));
        if (!result.accountFound()) {
            return ApiAuthFilter.json(Response.Status.NOT_FOUND, "no_portal_account",
                    "No portal account is registered for this e-mail; offer registration.");
        }
        return Response.ok("{\"resetSent\":true}").build();
    }

    /**
     * Staff handoff from a partner practice portal: the partner posts the signed
     * assertion it minted for the staff member, and the clinic opens a portal
     * session for the matching account so the clinician can continue here.
     */
    @POST
    @Path("/partner-session")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response partnerSession(@FormParam("assertion") String assertion,
                                   @Context HttpServletRequest request) {
        if (assertion == null || assertion.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "bad_request", "The handoff assertion is missing.");
        }
        String owner;
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(PARTNER_HANDOFF_KEY)
                    .build()
                    .parseClaimsJwt(assertion)
                    .getBody();
            owner = claims.getSubject();
        } catch (Exception e) {
            return ApiAuthFilter.json(Response.Status.UNAUTHORIZED, "bad_assertion",
                    "The handoff assertion could not be read.");
        }
        User user = auth.findByEmail(owner);
        if (user == null) {
            return ApiAuthFilter.json(Response.Status.UNAUTHORIZED, "bad_assertion",
                    "No portal account matches this handoff.");
        }
        auth.recordLoginSuccess(user, DecisionContextFilter.clientIp(request));
        DecisionService decision = CDI.current().select(DecisionService.class).get();
        DecisionService.SessionIssue issuedSession = decision.issueSession(user,
                DecisionContextFilter.clientIp(request), request.getHeader("User-Agent"), false);
        return Response.ok("{\"session\":\"" + issuedSession.token() + "\","
                        + "\"email\":\"" + JsonEsc.escape(user.getEmail()) + "\","
                        + "\"fullName\":\"" + JsonEsc.escape(user.getFullName()) + "\","
                        + "\"role\":\"" + user.getRole().name() + "\"}")
                .build();
    }

    @GET
    @Path("/me")
    public Response me(@Context HttpServletRequest request) {
        DecisionContext ctx = ApiAuthFilter.ctx(request);
        if (ctx.getUserId() == null) {
            return ApiAuthFilter.json(Response.Status.FORBIDDEN, "api_key_session",
                    "This endpoint requires a user session token, not an API key.");
        }
        return Response.ok("{\"userId\":" + ctx.getUserId()
                        + ",\"email\":\"" + JsonEsc.escape(ctx.getEmail()) + "\""
                        + ",\"fullName\":\"" + JsonEsc.escape(ctx.getFullName()) + "\""
                        + ",\"role\":\"" + ctx.getRole() + "\"}")
                .build();
    }
}
