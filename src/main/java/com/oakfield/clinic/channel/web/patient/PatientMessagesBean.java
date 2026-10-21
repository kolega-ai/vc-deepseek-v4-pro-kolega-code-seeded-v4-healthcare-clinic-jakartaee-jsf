package com.oakfield.clinic.channel.web.patient;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Message;
import com.oakfield.clinic.model.MessageThread;
import com.oakfield.clinic.svc.MessageService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Patient secure messaging: thread list, thread view, new thread, replies. */
@Named("ptMessages")
@ViewScoped
public class PatientMessagesBean implements Serializable {

    private List<MessageThread> myThreads;
    private Set<Long> unreadThreadIds = new HashSet<>();
    private long selectedThreadId;
    private MessageService.ThreadView threadView;
    private String newSubject;
    private String newBody;
    private String replyBody;

    @Inject
    private SessionInfo session;

    @Inject
    private MessageService messages;

    @Inject
    private PatientAccessService patientAccess;

    @PostConstruct
    public void load() {
        myThreads = messages.myThreads(session.ctx());
        unreadThreadIds = new HashSet<>();
        for (MessageThread t : myThreads) {
            if (messages.isUnread(session.ctx(), t)) {
                unreadThreadIds.add(t.getId());
            }
        }
    }

    public void openThread(long id) {
        selectedThreadId = id;
        threadView = messages.openThread(session.ctx(), id);
        load();
    }

    public void sendNew() {
        try {
            long patientId = patientAccess.patientForUser(session.ctx()).getId();
            MessageThread t = messages.startThread(session.ctx(), patientId, newSubject, newBody);
            newSubject = null;
            newBody = null;
            load();
            openThread(t.getId());
        } catch (ValidationException e) {
            FacesUtil.error("Could not send", e.getMessage());
        }
    }

    public void reply() {
        try {
            messages.reply(session.ctx(), selectedThreadId, replyBody);
            replyBody = null;
            threadView = messages.openThread(session.ctx(), selectedThreadId);
        } catch (ValidationException e) {
            FacesUtil.error("Could not reply", e.getMessage());
        }
    }

    public long getUnreadCount() {
        return messages.unreadThreadCount(session.ctx());
    }

    public boolean isUnread(long threadId) { return unreadThreadIds.contains(threadId); }

    /** True when the message was written by the acting user (renders as the "out" bubble). */
    public boolean isOut(Message m) {
        return m.getSenderUserId() != null && m.getSenderUserId().equals(session.ctx().getUserId());
    }

    public List<MessageThread> getMyThreads() { return myThreads; }
    public long getSelectedThreadId() { return selectedThreadId; }
    public void setSelectedThreadId(long selectedThreadId) { this.selectedThreadId = selectedThreadId; }
    public MessageService.ThreadView getThreadView() { return threadView; }
    public String getNewSubject() { return newSubject; }
    public void setNewSubject(String newSubject) { this.newSubject = newSubject; }
    public String getNewBody() { return newBody; }
    public void setNewBody(String newBody) { this.newBody = newBody; }
    public String getReplyBody() { return replyBody; }
    public void setReplyBody(String replyBody) { this.replyBody = replyBody; }
}
