package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/** Clinic-admin provider directory: list and create/edit providers (NPI, contact, active flag). */
@Named("adminProviders")
@ViewScoped
public class AdminProvidersBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private ProviderService providers;

    private List<Provider> providerList;
    private long editId = -1;
    private Part directoryFile;

    private String firstName;
    private String lastName;
    private String title;
    private String specialty;
    private String npi;
    private String phone;
    private String email;
    private boolean active = true;

    @PostConstruct
    public void load() {
        providerList = providers.listAll();
    }

    public boolean isEditing() {
        return editId >= 0;
    }

    public void startCreate() {
        editId = -1;
        firstName = null;
        lastName = null;
        title = null;
        specialty = null;
        npi = null;
        phone = null;
        email = null;
        active = true;
    }

    public void startEdit(Provider p) {
        editId = p.getId();
        firstName = p.getFirstName();
        lastName = p.getLastName();
        title = p.getTitle();
        specialty = p.getSpecialty();
        npi = p.getNpi();
        phone = p.getPhone();
        email = p.getEmail();
        active = p.isActive();
    }

    public void cancelEdit() {
        editId = -1;
    }

    public void save() {
        try {
            if (editId >= 0) {
                providers.update(session.ctx(), editId, firstName, lastName, title, specialty,
                        phone, email, active);
            } else {
                providers.create(session.ctx(), firstName, lastName, title, specialty, npi, phone, email);
            }
            FacesUtil.info("Provider saved", editId >= 0 ? "Provider details were updated." : "Provider was added.");
            cancelEdit();
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not save provider", e.getMessage());
        }
    }

    /** Refresh the local roster from the clinic's staff directory. */
    public void syncDirectory() {
        if (directoryFile == null || directoryFile.getSize() == 0) {
            FacesUtil.error("No roster selected", "Pick a roster file with one directory account per line.");
            return;
        }
        try {
            int synced = providers.syncFromDirectory(session.ctx(), directoryFile.getInputStream());
            FacesUtil.info("Directory synced", synced + " provider record(s) refreshed from the directory.");
            directoryFile = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Directory sync failed", e.getMessage());
        } catch (IOException e) {
            FacesUtil.error("Directory sync failed", "Could not read the roster file.");
        }
    }

    public Part getDirectoryFile() { return directoryFile; }
    public void setDirectoryFile(Part directoryFile) { this.directoryFile = directoryFile; }

    public List<Provider> getProviderList() { return providerList; }
    public long getEditId() { return editId; }
    public void setEditId(long editId) { this.editId = editId; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getNpi() { return npi; }
    public void setNpi(String npi) { this.npi = npi; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
