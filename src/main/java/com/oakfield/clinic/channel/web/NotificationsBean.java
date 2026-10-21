package com.oakfield.clinic.channel.web;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Notification;
import com.oakfield.clinic.svc.NotificationService;
import com.oakfield.clinic.svc.PageResult;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** In-app notification center. */
@Named("notifications")
@ViewScoped
public class NotificationsBean implements Serializable {

    private PageResult<Notification> page;
    private int pageIndex;

    @Inject
    private SessionInfo session;

    @Inject
    private NotificationService service;

    @PostConstruct
    public void load() {
        pageIndex = 0;
        refresh();
    }

    private void refresh() {
        page = new PageResult<>(service.forUser(session.ctx().getUserId(), pageIndex, 20),
                service.countForUser(session.ctx().getUserId()), pageIndex, 20);
    }

    public boolean hasMore() {
        return page.items().size() >= 20;
    }

    public void nextPage() {
        pageIndex++;
        refresh();
    }

    public void prevPage() {
        if (pageIndex > 0) {
            pageIndex--;
            refresh();
        }
    }

    public void markRead(long id) {
        service.markRead(session.ctx(), id);
        refresh();
    }

    public void markAllRead() {
        service.markAllRead(session.ctx());
        FacesUtil.info("All caught up", "Every notification is marked as read.");
        refresh();
    }

    public PageResult<Notification> getPage() { return page; }
    public int getPageIndex() { return pageIndex; }
}
