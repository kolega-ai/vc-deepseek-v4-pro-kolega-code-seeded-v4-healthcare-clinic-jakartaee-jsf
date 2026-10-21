package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.svc.AuthService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Completes a password reset with a single-use, time-bound token. */
@Named("resetPassword")
@RequestScoped
public class ResetPasswordBean implements Serializable {

    private String token;
    private String newPassword;
    private String confirmPassword;
    private boolean validToken;

    @Inject
    private AuthService auth;

    public void onLoad() {
        // Validity is re-checked server-side on submit; the form only renders with a token present.
        validToken = token != null && !token.isBlank();
    }

    public String reset() {
        if (!newPassword.equals(confirmPassword)) {
            FacesUtil.error("Passwords do not match", "Please enter the same password twice.");
            return null;
        }
        try {
            auth.resetPassword(token, newPassword);
        } catch (ValidationException e) {
            validToken = false;
            FacesUtil.error("Could not reset your password", e.getMessage());
            return null;
        }
        FacesUtil.info("Password updated", "Sign in with your new password.");
        FacesUtil.redirect(FacesUtil.request().getContextPath() + "/login.xhtml");
        return null;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public boolean isValidToken() { return validToken; }
}
