package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.model.OutboxChannel;
import com.oakfield.clinic.model.OutboxMessage;
import com.oakfield.clinic.svc.OutboxService;
import com.oakfield.clinic.svc.PageResult;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Clinic-admin outbox viewer: locally mocked email/SMS messages "sent" by a background job. */
@Named("adminOutbox")
@ViewScoped
public class AdminOutboxBean implements Serializable {

    @Inject
    private OutboxService outbox;

    private PageResult<OutboxMessage> page;
    private int pageIndex;

    @PostConstruct
    public void load() {
        page = new PageResult<>(outbox.list(pageIndex, 25), outbox.count(), pageIndex, 25);
    }

    public void nextPage() {
        pageIndex++;
        load();
    }

    public void prevPage() {
        if (pageIndex > 0) {
            pageIndex--;
            load();
        }
    }

    public String channelLabel(OutboxChannel c) {
        return c == OutboxChannel.SMS ? "SMS" : "Email";
    }

    public PageResult<OutboxMessage> getPage() { return page; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
}
