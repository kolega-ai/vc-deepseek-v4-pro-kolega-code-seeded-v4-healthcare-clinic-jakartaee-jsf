package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Message;
import com.oakfield.clinic.model.MessageThread;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.svc.MessageService;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff messaging: pick a patient (search), browse their threads, open a thread,
 * reply (patient-visible) or add internal notes (staff-only).
 */
@Named("staffMessages")
@ViewScoped
public class StaffMessagesBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private MessageService messages;
    @Inject
    private PatientService patients;

    private Long selectedPatientId;
    private Long selectedThreadId;
    private String replyBody;
    private String noteBody;
    private String newSubject;
    private String newBody;
    private Long newPatientId;

    private String patientQuery;
    private List<Patient> patientResults = new ArrayList<>();

    private List<MessageThread> threads = new ArrayList<>();
    private MessageService.ThreadView threadView;
    private Patient selectedPatient;

    @PostConstruct
    public void load() {
        long patientParam = FacesUtil.paramLong("patient", -1);
        long threadParam = FacesUtil.paramLong("thread", -1);
        threads = new ArrayList<>();
        threadView = null;
        if (threadParam >= 0) {
            try {
                openThread(threadParam);
            } catch (ValidationException e) {
                FacesUtil.error("Could not open conversation", e.getMessage());
            }
        } else if (patientParam >= 0) {
            selectPatient(patientParam);
        }
    }

    public void searchPatients() {
        try {
            patientResults = patients.search(session.ctx(),
                    new PatientService.PatientFilter(patientQuery, null, true), 0, 10, "name").items();
        } catch (ValidationException e) {
            FacesUtil.error("Search failed", e.getMessage());
        }
    }

    public void selectPatient(long patientId) {
        selectedPatientId = patientId;
        newPatientId = patientId;
        selectedPatient = patients.getForStaff(session.ctx(), patientId);
        threads = messages.threadsForPatient(session.ctx(), patientId);
    }

    public void openThread(long threadId) {
        threadView = messages.openThread(session.ctx(), threadId);
        selectedThreadId = threadId;
        selectedPatientId = threadView.thread().getPatient().getId();
        newPatientId = selectedPatientId;
        selectedPatient = threadView.thread().getPatient();
        threads = messages.threadsForPatient(session.ctx(), selectedPatientId);
    }

    public void startNewThread() {
        Long target = newPatientId != null ? newPatientId : selectedPatientId;
        if (target == null) {
            FacesUtil.error("No patient", "Choose a patient first.");
            return;
        }
        try {
            messages.staffStartThread(session.ctx(), target, newSubject, newBody);
            FacesUtil.info("Message sent", "The patient was notified.");
            newSubject = null;
            newBody = null;
            selectPatient(target);
        } catch (ValidationException e) {
            FacesUtil.error("Could not send", e.getMessage());
        }
    }

    public void reply() {
        try {
            messages.reply(session.ctx(), selectedThreadId, replyBody);
            replyBody = null;
            FacesUtil.info("Reply sent", "The patient was notified.");
            openThread(selectedThreadId);
        } catch (ValidationException e) {
            FacesUtil.error("Could not reply", e.getMessage());
        }
    }

    public void addInternalNote() {
        try {
            messages.addInternalNote(session.ctx(), selectedThreadId, noteBody);
            noteBody = null;
            FacesUtil.info("Note added", "The internal note was saved.");
            openThread(selectedThreadId);
        } catch (ValidationException e) {
            FacesUtil.error("Could not add note", e.getMessage());
        }
    }

    public boolean isUnread(MessageThread t) {
        return t != null && messages.isUnread(session.ctx(), t);
    }

    public boolean isMine(Message m) {
        return m != null && m.getSenderUserId() != null
                && m.getSenderUserId().equals(session.ctx().getUserId());
    }

    public Long getSelectedPatientId() {
        return selectedPatientId;
    }

    public void setSelectedPatientId(Long selectedPatientId) {
        this.selectedPatientId = selectedPatientId;
    }

    public Long getSelectedThreadId() {
        return selectedThreadId;
    }

    public void setSelectedThreadId(Long selectedThreadId) {
        this.selectedThreadId = selectedThreadId;
    }

    public String getReplyBody() {
        return replyBody;
    }

    public void setReplyBody(String replyBody) {
        this.replyBody = replyBody;
    }

    public String getNoteBody() {
        return noteBody;
    }

    public void setNoteBody(String noteBody) {
        this.noteBody = noteBody;
    }

    public String getNewSubject() {
        return newSubject;
    }

    public void setNewSubject(String newSubject) {
        this.newSubject = newSubject;
    }

    public String getNewBody() {
        return newBody;
    }

    public void setNewBody(String newBody) {
        this.newBody = newBody;
    }

    public Long getNewPatientId() {
        return newPatientId;
    }

    public void setNewPatientId(Long newPatientId) {
        this.newPatientId = newPatientId;
    }

    public String getPatientQuery() {
        return patientQuery;
    }

    public void setPatientQuery(String patientQuery) {
        this.patientQuery = patientQuery;
    }

    public List<Patient> getPatientResults() {
        return patientResults;
    }

    public List<MessageThread> getThreads() {
        return threads;
    }

    public MessageService.ThreadView getThreadView() {
        return threadView;
    }

    public Patient getSelectedPatient() {
        return selectedPatient;
    }
}
