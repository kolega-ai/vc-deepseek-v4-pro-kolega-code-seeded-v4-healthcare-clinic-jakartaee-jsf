package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.svc.AuthService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;

/** Invite activation for staff and patients (token is single-use, 7 days). */
@Named("invite")
@RequestScoped
public class InviteBean implements Serializable {

    private String token;
    private String password;
    private String phone;
    private LocalDate dateOfBirth;

    private String email;
    private String role;
    private boolean valid;
    private String error;

    @Inject
    private AuthService auth;

    public void onLoad() {
        AuthService.InviteInfo info = auth.inviteInfo(token);
        email = info.email();
        role = info.role();
        valid = info.valid();
        error = info.error();
    }

    public boolean isPatientInvite() {
        return "PATIENT".equals(role);
    }

    public String accept() {
        try {
            auth.acceptInvite(token, password, phone, dateOfBirth);
        } catch (ValidationException e) {
            FacesUtil.error("Could not activate your account", e.getMessage());
            return null;
        }
        FacesUtil.info("Account activated", "Sign in with your new account.");
        FacesUtil.redirect(FacesUtil.request().getContextPath() + "/login.xhtml");
        return null;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isValid() { return valid; }
    public String getError() { return error; }
}
