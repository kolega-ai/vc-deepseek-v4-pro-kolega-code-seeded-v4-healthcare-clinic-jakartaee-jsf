package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.decision.DecisionContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;

/** Exposes the decision context resolved for the current request to views. */
@Named("appSession")
@RequestScoped
public class SessionInfo implements Serializable {

    public DecisionContext ctx() {
        HttpServletRequest request = (HttpServletRequest)
                FacesContext.getCurrentInstance().getExternalContext().getRequest();
        return DecisionContextFilter.ctxOf(request);
    }

    public boolean isPatient() { return ctx().isPatient(); }
    public boolean isStaff() { return ctx().isStaff(); }
    public boolean isAdmin() { return "CLINIC_ADMIN".equals(ctx().getRole()); }
    public boolean isClinician() { return "CLINICIAN".equals(ctx().getRole()); }
    public boolean isFrontDesk() { return "FRONT_DESK".equals(ctx().getRole()); }

    public String userLabel() {
        DecisionContext ctx = ctx();
        if ("CLINIC_ADMIN".equals(ctx.getRole())) return "Clinic Admin";
        if ("CLINICIAN".equals(ctx.getRole())) return "Clinician";
        if ("FRONT_DESK".equals(ctx.getRole())) return "Front Desk";
        return "Patient";
    }

    public String initials() {
        String name = ctx().getFullName() == null ? "?" : ctx().getFullName().strip();
        String[] parts = name.split("\\s+");
        if (parts.length == 0) return "?";
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
