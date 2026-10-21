package com.oakfield.clinic.channel.web.frontdesk;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** Front desk queue of SUBMITTED form documents to verify or reject. */
@Named("formVerification")
@ViewScoped
public class FormVerificationBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private DocumentService documents;

    private List<Document> queue = Collections.emptyList();
    private String verifyNote;
    private String rejectNote;

    @PostConstruct
    public void load() {
        queue = documents.verificationQueue(session.ctx());
    }

    public void verify(long id) {
        try {
            documents.verify(session.ctx(), id, verifyNote);
            verifyNote = null;
            FacesUtil.info("Document verified", "The document was reviewed and accepted.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not verify the document", e.getMessage());
        }
        load();
    }

    public void reject(long id) {
        try {
            documents.reject(session.ctx(), id, rejectNote);
            rejectNote = null;
            FacesUtil.info("Document rejected", "The patient has been notified.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not reject the document", e.getMessage());
        }
        load();
    }

    public List<Document> getQueue() { return queue; }

    public String getVerifyNote() { return verifyNote; }
    public void setVerifyNote(String verifyNote) { this.verifyNote = verifyNote; }

    public String getRejectNote() { return rejectNote; }
    public void setRejectNote(String rejectNote) { this.rejectNote = rejectNote; }
}
