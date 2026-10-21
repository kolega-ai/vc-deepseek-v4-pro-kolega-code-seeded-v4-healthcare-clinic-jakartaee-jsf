package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.AuthService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.UserAdminService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Profile page: contact details (patients), account info, and password change. */
@Named("profile")
@ViewScoped
public class ProfileBean implements Serializable {

    @Inject
    private SessionInfo session;

    @Inject
    private PatientAccessService patientAccess;

    @Inject
    private PatientService patients;

    @Inject
    private AuthService auth;

    private Patient patient;
    private String phone;
    private String email;
    private String addressLine1;
    private String city;
    private String state;
    private String postalCode;
    private String emergencyContactName;
    private String emergencyContactPhone;

    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    @PostConstruct
    public void load() {
        DecisionContext ctx = session.ctx();
        if (ctx.isPatient()) {
            patient = patientAccess.patientForUser(ctx);
            phone = patient.getPhone();
            email = patient.getEmail();
            addressLine1 = patient.getAddressLine1();
            city = patient.getCity();
            state = patient.getState();
            postalCode = patient.getPostalCode();
            emergencyContactName = patient.getEmergencyContactName();
            emergencyContactPhone = patient.getEmergencyContactPhone();
        }
    }

    public String getAccountEmail() { return session.ctx().getEmail(); }
    public String getRoleLabel() { return session.userLabel(); }

    public String saveContact() {
        try {
            patients.updateOwnProfile(session.ctx(), phone, email, addressLine1, city, state, postalCode,
                    emergencyContactName, emergencyContactPhone);
        } catch (ValidationException e) {
            FacesUtil.error("Could not save your details", e.getMessage());
            return null;
        }
        FacesUtil.info("Profile updated", "Your contact details were saved.");
        return null;
    }

    public String changePassword() {
        if (!newPassword.equals(confirmPassword)) {
            FacesUtil.error("Passwords do not match", "Please enter the same new password twice.");
            return null;
        }
        try {
            auth.changePassword(session.ctx(), currentPassword, newPassword);
        } catch (ValidationException e) {
            FacesUtil.error("Could not change your password", e.getMessage());
            return null;
        }
        currentPassword = null;
        newPassword = null;
        confirmPassword = null;
        FacesUtil.info("Password changed", "Use your new password next time you sign in.");
        return null;
    }

    public Patient getPatient() { return patient; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
