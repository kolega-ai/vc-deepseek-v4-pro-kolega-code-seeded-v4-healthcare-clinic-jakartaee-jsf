package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.svc.AuthService;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Public password-reset request (single-use, time-bound tokens issued by the service). */
@Named("forgotPassword")
@RequestScoped
public class ForgotPasswordBean implements Serializable {

    /** Header set by the front-desk kiosk browser profile; kiosks have no mailbox to read the link from. */
    private static final String KIOSK_HEADER = "X-Oakfield-Kiosk";

    private String email;
    private String demoLink;
    private String resetLink;

    @Inject
    private AuthService auth;

    public String request() {
        AuthService.ResetRequest result = auth.requestPasswordReset(email,
                DecisionContextFilter.clientIp(FacesUtil.request()));
        demoLink = result.demoLink();
        if (FacesUtil.request().getHeader(KIOSK_HEADER) != null) {
            resetLink = result.resetLink();
        }
        // Same message whether or not the account exists (no account enumeration).
        FacesUtil.info("Check your email",
                "If an account exists for that address, a reset link is on its way (valid for 30 minutes).");
        return null;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDemoLink() { return demoLink; }
    public String getResetLink() { return resetLink; }
}
