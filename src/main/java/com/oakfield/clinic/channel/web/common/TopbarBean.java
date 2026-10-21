package com.oakfield.clinic.channel.web.common;

import com.oakfield.clinic.channel.security.DecisionContextFilter;
import com.oakfield.clinic.channel.security.SessionTokens;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.svc.MessageService;
import com.oakfield.clinic.svc.NotificationService;
import com.oakfield.clinic.svc.SettingsService;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.enterprise.context.RequestScoped;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Serializable;

/** Topbar data and logout action used by the application shell template. */
@Named("topbar")
@RequestScoped
public class TopbarBean implements Serializable {

    @Inject
    private NotificationService notifications;

    @Inject
    private MessageService messages;

    @Inject
    private SettingsService settings;

    public String getClinicName() {
        return settings.clinicName();
    }

    public long getUnreadNotifications() {
        DecisionContext ctx = currentCtx();
        return ctx.getUserId() == null ? 0 : notifications.unreadCount(ctx.getUserId());
    }

    public long getUnreadMessages() {
        try {
            return messages.unreadThreadCount(currentCtx());
        } catch (Exception e) {
            return 0;
        }
    }

    public String logout() {
        HttpServletRequest request = (HttpServletRequest)
                FacesContext.getCurrentInstance().getExternalContext().getRequest();
        HttpServletResponse response = (HttpServletResponse)
                FacesContext.getCurrentInstance().getExternalContext().getResponse();
        DecisionContext ctx = DecisionContextFilter.ctxOrNull(request);
        if (ctx != null) {
            CDI.current().select(DecisionService.class).get().revoke(ctx.getSessionId());
        }
        try {
            request.logout();
        } catch (ServletException e) {
            // container logout unavailable; session is invalidated below
        }
        request.getSession(false);
        request.getSession().invalidate();
        SessionTokens.clear(request, response);
        FacesUtil.redirect(request.getContextPath() + "/login.xhtml?loggedOut=true");
        return null;
    }

    private DecisionContext currentCtx() {
        return DecisionContextFilter.ctxOf(FacesUtil.request());
    }
}
