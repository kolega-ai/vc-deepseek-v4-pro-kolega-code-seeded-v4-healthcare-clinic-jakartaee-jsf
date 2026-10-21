package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.channel.security.SessionTokens;
import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.AuthService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.Serializable;

/** Login page: completes the decision-token handshake after container authentication. */
@Named("login")
@RequestScoped
public class LoginBean implements Serializable {

    private String email;
    private String password;
    private String returnTo;
    private boolean loggedOutFlag;
    private boolean expiredFlag;
    private boolean registeredFlag;

    @Inject
    private AuthService auth;

    @Inject
    private DecisionService decision;

    /** Runs on page load (f:viewAction, GET and postback): redirects authenticated users. */
    public void onLoad() {
        if (loggedOutFlag) FacesUtil.info("Signed out", "You have been signed out of the portal.");
        if (expiredFlag) FacesUtil.warn("Session expired", "Please sign in again to continue.");
        if (registeredFlag) FacesUtil.info("Account created", "Sign in with your new account to get started.");
        HttpServletRequest request = FacesUtil.request();
        HttpServletResponse response = (HttpServletResponse)
                FacesContext.getCurrentInstance().getExternalContext().getResponse();
        if (returnTo != null && !SessionTokens.validReturnTo(returnTo)) {
            returnTo = null;
        }
        Object loginTarget = request.getAttribute("loginTarget");
        if (loginTarget instanceof String target && returnTo == null) {
            returnTo = target;
        }
        if (request.getUserPrincipal() != null) {
            if (DecisionContextFilter.ctxOrNull(request) != null) {
                redirectTo(returnTo != null ? returnTo : dashboard());
            } else {
                // Lost decision cookie: re-mint from the authenticated identity.
                User user = auth.findByEmail(request.getUserPrincipal().getName());
                if (user != null) {
                    DecisionService.SessionIssue issue = decision.issueSession(user,
                            DecisionContextFilter.clientIp(request), request.getHeader("User-Agent"));
                    SessionTokens.set(response, issue.token());
                    redirectTo(returnTo != null ? returnTo : dashboard());
                }
            }
        }
    }

    private String dashboard() {
        return "/app/dashboard.xhtml"; // single role-aware dashboard
    }

    private void redirectTo(String path) {
        FacesUtil.redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + path);
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getReturnTo() { return returnTo; }
    public void setReturnTo(String returnTo) { this.returnTo = returnTo; }
    public boolean isLoggedOutFlag() { return loggedOutFlag; }
    public void setLoggedOutFlag(boolean loggedOutFlag) { this.loggedOutFlag = loggedOutFlag; }
    public boolean isExpiredFlag() { return expiredFlag; }
    public void setExpiredFlag(boolean expiredFlag) { this.expiredFlag = expiredFlag; }
    public boolean isRegisteredFlag() { return registeredFlag; }
    public void setRegisteredFlag(boolean registeredFlag) { this.registeredFlag = registeredFlag; }
}
