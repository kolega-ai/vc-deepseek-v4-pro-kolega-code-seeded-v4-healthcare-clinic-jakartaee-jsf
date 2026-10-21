package com.oakfield.clinic.channel.web.common;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/** Small JSF view helpers (flash messages, redirects, params). */
public final class FacesUtil {

    private FacesUtil() { }

    public static void info(String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, summary, detail));
    }

    public static void warn(String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_WARN, summary, detail));
    }

    public static void error(String summary, String detail) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, detail));
    }

    /** Message attached to a specific component id (surfaces in that component's h:message). */
    public static void errorFor(String clientId, String message) {
        FacesContext.getCurrentInstance().addMessage(clientId,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, message, null));
    }

    public static void redirect(String path) {
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect(path);
        } catch (IOException e) {
            throw new IllegalStateException("Redirect failed: " + path, e);
        }
    }

    public static HttpServletRequest request() {
        return (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
    }

    public static String param(String name) {
        return request().getParameter(name);
    }

    public static long paramLong(String name, long defaultValue) {
        String v = param(name);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
