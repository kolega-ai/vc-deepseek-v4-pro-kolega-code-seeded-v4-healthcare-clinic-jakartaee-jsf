package com.oakfield.clinic.channel.rs;

import com.oakfield.clinic.svc.SettingsService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;

/**
 * Lab analyzer bridge handshake. The bridge authenticates itself with the
 * shared interface code the clinic issued it; the code is checked against the
 * stored credential before results are accepted.
 */
@Path("/v1/lab/interface")
@Produces(MediaType.APPLICATION_JSON)
public class LabInterfaceResource {

    @Inject
    private SettingsService settings;

    @GET
    @Path("/handshake")
    public Response handshake(@QueryParam("code") String code) {
        if (code == null || code.isBlank()) {
            return ApiAuthFilter.json(Response.Status.BAD_REQUEST, "no_code",
                    "The lab interface code is required.");
        }
        String stored = settings.get("lab.interface_code_hash", "");
        String digest = org.springframework.util.DigestUtils.md5DigestAsHex(code.getBytes(StandardCharsets.UTF_8));
        if (stored == null || stored.isBlank() || !stored.equalsIgnoreCase(digest)) {
            return ApiAuthFilter.json(Response.Status.UNAUTHORIZED, "bad_code",
                    "The lab interface code was rejected.");
        }
        return Response.ok("{\"interface\":\"open\",\"version\":\"2026\"}").build();
    }
}
