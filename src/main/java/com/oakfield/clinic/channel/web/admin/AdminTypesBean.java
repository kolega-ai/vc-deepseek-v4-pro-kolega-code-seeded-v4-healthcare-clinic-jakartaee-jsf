package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.svc.AppointmentTypeService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Clinic-admin appointment types (fee schedule). Fees are fixed-precision
 * decimals read server-side only - never accepted from clients in booking flows.
 */
@Named("adminTypes")
@ViewScoped
public class AdminTypesBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private AppointmentTypeService types;

    private List<AppointmentType> typeList;
    private long editId = -1;

    private String code;
    private String name;
    private String description;
    private int durationMinutes = 30;
    private BigDecimal feeAmount;
    private String feeCurrency = "USD";
    private boolean active = true;

    @PostConstruct
    public void load() {
        typeList = types.listAll();
    }

    public boolean isEditing() {
        return editId >= 0;
    }

    public void startCreate() {
        editId = -1;
        code = null;
        name = null;
        description = null;
        durationMinutes = 30;
        feeAmount = null;
        feeCurrency = "USD";
        active = true;
    }

    public void startEdit(AppointmentType t) {
        editId = t.getId();
        code = t.getCode();
        name = t.getName();
        description = t.getDescription();
        durationMinutes = t.getDurationMinutes();
        feeAmount = t.getFeeAmount();
        feeCurrency = t.getFeeCurrency();
        active = t.isActive();
    }

    public void cancelEdit() {
        editId = -1;
    }

    public void save() {
        try {
            if (editId >= 0) {
                types.update(session.ctx(), editId, name, description, durationMinutes,
                        feeAmount, feeCurrency, active);
            } else {
                String c = code == null ? "" : code.strip();
                if (typeList.stream().anyMatch(t -> t.getCode().equalsIgnoreCase(c))) {
                    throw new ValidationException("A type with code '" + c + "' already exists. Edit that row instead.");
                }
                types.create(session.ctx(), c, name, description, durationMinutes,
                        feeAmount, feeCurrency, active);
            }
            FacesUtil.info("Appointment type saved", editId >= 0 ? "Type updated." : "Type created.");
            cancelEdit();
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not save appointment type", e.getMessage());
        }
    }

    public List<AppointmentType> getTypeList() { return typeList; }
    public long getEditId() { return editId; }
    public void setEditId(long editId) { this.editId = editId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public String getFeeCurrency() { return feeCurrency; }
    public void setFeeCurrency(String feeCurrency) { this.feeCurrency = feeCurrency; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
