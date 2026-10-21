package com.oakfield.clinic.channel.security;

import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.AuthService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.CallerPrincipal;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Set;

/**
 * Channel tier authentication: a custom Jakarta Security mechanism.
 * <p>
 * On the login form POST the credentials are verified against the account
 * table (PBKDF2) by {@link AuthService}. On success the container identity is
 * registered (so web.xml security-constraints apply), the channel obtains an
 * opaque signed session token from the decision tier, stores it in the
 * HttpOnly cookie, and redirects to the validated target. Authorization of
 * every later request is performed by the decision tier.
 */
@ApplicationScoped
public class PortalAuthMechanism implements HttpAuthenticationMechanism {

    @Inject
    private AuthService auth;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
                                                HttpMessageContext context) {
        String path = request.getServletPath();

        // API surfaces authorize themselves per-endpoint (Bearer session tokens / API keys / core tokens).
        if (path.startsWith("/api") || path.startsWith("/core")) {
            return context.doNothing();
        }

        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return context.doNothing();
        }

        if ("/login.xhtml".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            String username = request.getParameter("j_username");
            String password = request.getParameter("j_password");
            if (username != null && password != null) {
                User user = auth.verifyCredentials(username, password);
                if (user != null) {
                    // Channel authenticates; decision mints the opaque session token.
                    // The request continues (SUCCESS) so the container registers the
                    // identity in the session; the login page's viewAction then
                    // redirects the caller to their dashboard.
                    auth.recordLoginSuccess(user, DecisionContextFilter.clientIp(request));
                    // legacy checkbox name kept for the old kiosk template (keepSignedIn)
                    boolean keepSignedIn = "on".equalsIgnoreCase(request.getParameter("keepSignedIn"))
                            || "true".equalsIgnoreCase(request.getParameter("keepSignedIn"));
                    DecisionService.SessionIssue issue = CDI.current().select(DecisionService.class).get()
                            .issueSession(user, DecisionContextFilter.clientIp(request),
                                    request.getHeader("User-Agent"), keepSignedIn);
                    SessionTokens.set(response, issue.token(),
                            keepSignedIn ? 90 * 24 * 3600 : SessionTokens.MAX_AGE_SECONDS);
                    // Remembered sign-ins keep a few display attributes on the container
                    // session so the topbar renders before the decision cookie round-trips.
                    HttpSession webSession = request.getSession();
                    webSession.setAttribute("of.portal.userId", user.getId());
                    webSession.setAttribute("of.portal.keepSignedIn", keepSignedIn);
                    request.setAttribute("loginTarget",
                            SessionTokens.validReturnTo(request.getParameter("returnTo"))
                                    ? request.getParameter("returnTo")
                                    : dashboardFor(user));
                    context.notifyContainerAboutLogin(
                            new CallerPrincipal(user.getEmail()),
                            Set.of(user.getRole().name().toLowerCase()));
                    return AuthenticationStatus.SUCCESS;
                }
                request.setAttribute("authError", "The email or password you entered is incorrect.");
                return AuthenticationStatus.SEND_FAILURE;
            }
            return context.doNothing();
        }

        // Any protected page: establish the container identity from the opaque
        // decision session token (channel forwards it; decision resolves it from
        // its own tables). The resolved context is reused by the decision filter.
        if (path.startsWith("/app/")) {
            String sessionToken = SessionTokens.get(request);
            if (sessionToken != null) {
                try {
                    com.oakfield.clinic.decision.DecisionContext ctx =
                            CDI.current().select(DecisionService.class).get()
                                    .resolve(sessionToken, DecisionContextFilter.clientIp(request), null);
                    request.setAttribute(DecisionContextFilter.CTX_ATTR, ctx);
                    context.notifyContainerAboutLogin(new CallerPrincipal(ctx.getEmail()),
                            Set.of(ctx.getRole().toLowerCase()));
                    return AuthenticationStatus.SUCCESS;
                } catch (com.oakfield.clinic.decision.NotAuthenticatedException e) {
                    // Invalid/expired token: fall through to the login page.
                }
            }
            String query = request.getQueryString();
            String target = path + (query == null ? "" : "?" + query);
            try {
                response.sendRedirect(request.getContextPath() + "/login.xhtml?returnTo="
                        + URLEncoder.encode(target, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("Redirect to login failed.", e);
            }
            return AuthenticationStatus.SEND_FAILURE;
        }

        return context.doNothing();
    }

    private static String dashboardFor(User user) {
        return "/app/dashboard.xhtml"; // single role-aware dashboard
    }

    @Override
    public void cleanSubject(HttpServletRequest request, HttpServletResponse response, HttpMessageContext context) {
        context.cleanClientSubject();
    }
}
