package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.svc.SettingsService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Clinic-admin settings editor: pick a key from the table, edit its value. */
@Named("adminSettings")
@ViewScoped
public class AdminSettingsBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private SettingsService settings;

    private Map<String, String> settingsMap;
    private String selectedKey;
    private String editValue;
    private Part packFile;

    @PostConstruct
    public void load() {
        settingsMap = settings.all();
    }

    public List<Map.Entry<String, String>> getEntries() {
        return settingsMap == null ? List.of() : new ArrayList<>(settingsMap.entrySet());
    }

    public void selectKey(String key) {
        selectedKey = key;
        editValue = settingsMap == null ? null : settingsMap.get(key);
    }

    public void cancelEdit() {
        selectedKey = null;
        editValue = null;
    }

    public boolean isEditing() {
        return selectedKey != null;
    }

    public void save() {
        try {
            settings.set(session.ctx(), selectedKey, editValue);
            FacesUtil.info("Setting updated", selectedKey + " was saved.");
            cancelEdit();
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not update setting", e.getMessage());
        }
    }

    /** Apply a YAML configuration pack exported from staging or another clinic. */
    public void applyPack() {
        if (packFile == null || packFile.getSize() == 0) {
            FacesUtil.error("No pack selected", "Pick a configuration pack to apply.");
            return;
        }
        try {
            int applied = settings.applyConfigurationPack(session.ctx(), packFile.getInputStream());
            FacesUtil.info("Pack applied", applied + " setting(s) updated from the configuration pack.");
            packFile = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not apply the pack", e.getMessage());
        } catch (java.io.IOException e) {
            FacesUtil.error("Could not apply the pack", "Could not read the selected file.");
        }
    }

    public Map<String, String> getSettingsMap() { return settingsMap; }
    public String getSelectedKey() { return selectedKey; }
    public void setSelectedKey(String selectedKey) { this.selectedKey = selectedKey; }
    public String getEditValue() { return editValue; }
    public void setEditValue(String editValue) { this.editValue = editValue; }
    public Part getPackFile() { return packFile; }
    public void setPackFile(Part packFile) { this.packFile = packFile; }
}
