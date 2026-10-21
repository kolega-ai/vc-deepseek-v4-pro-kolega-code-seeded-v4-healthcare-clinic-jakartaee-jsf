package com.oakfield.clinic.channel.web.clinician;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.PatientAccessService;
import com.oakfield.clinic.svc.PrescriptionService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/** New prescription for a patient, from the chart. */
@Named("rxForm")
@ViewScoped
public class RxFormBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private PatientAccessService patientAccess;
    @Inject
    private AppointmentService appointments;
    @Inject
    private PrescriptionService prescriptions;

    private long patientId;
    private Patient patient;
    private List<Appointment> appts = Collections.emptyList();

    private String medicationName;
    private String dosage;
    private String frequency;
    private String instructions;
    private int refillsRemaining;
    private Long appointmentId;

    public void load() {
        patientId = FacesUtil.paramLong("patient", -1);
        patient = patientAccess.requirePatientAccess(session.ctx(), patientId,
                PatientAccessService.AccessLevel.CLINICIAN);
        appts = appointments.search(session.ctx(),
                new AppointmentService.AppointmentFilter(null, null, null, patientId, null), 0, 20).items();
    }

    public String save() {
        PrescriptionService.RxForm form = new PrescriptionService.RxForm(medicationName, dosage, frequency,
                instructions, refillsRemaining, appointmentId);
        try {
            prescriptions.create(session.ctx(), patientId, form);
        } catch (ValidationException e) {
            FacesUtil.error("Could not save the prescription", e.getMessage());
            return null;
        }
        FacesUtil.redirect(FacesUtil.request().getContextPath()
                + "/app/staff/clinician/chart.xhtml?id=" + patientId);
        return null;
    }

    public long getPatientId() { return patientId; }

    public Patient getPatient() { return patient; }

    public List<Appointment> getAppts() { return appts; }

    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public int getRefillsRemaining() { return refillsRemaining; }
    public void setRefillsRemaining(int refillsRemaining) { this.refillsRemaining = refillsRemaining; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
}
