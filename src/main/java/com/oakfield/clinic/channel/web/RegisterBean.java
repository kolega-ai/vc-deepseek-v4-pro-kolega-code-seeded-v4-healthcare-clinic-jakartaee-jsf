package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.svc.AuthService;
import com.oakfield.clinic.svc.SettingsService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;

/** Public patient self-registration. */
@Named("register")
@RequestScoped
public class RegisterBean implements Serializable {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String email;
    private String phone;
    private String password;
    private String confirmPassword;

    @Inject
    private AuthService auth;

    @Inject
    private SettingsService settings;

    public String register() {
        if (!password.equals(confirmPassword)) {
            FacesUtil.error("Passwords do not match", "Please enter the same password twice.");
            return null;
        }
        try {
            auth.registerPatient(new AuthService.RegistrationForm(
                    firstName, lastName, dateOfBirth, gender, email, password, phone),
                    DecisionContextFilter.clientIp(FacesUtil.request()));
        } catch (ValidationException e) {
            FacesUtil.error("Could not create your account", e.getMessage());
            return null;
        }
        FacesUtil.redirect(FacesUtil.request().getContextPath() + "/login.xhtml?registered=true");
        return null;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
