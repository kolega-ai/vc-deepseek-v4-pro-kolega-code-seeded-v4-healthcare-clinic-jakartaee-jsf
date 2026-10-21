package com.oakfield.clinic.channel.web.staff;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.model.Document;
import com.oakfield.clinic.model.Gender;
import com.oakfield.clinic.model.MessageThread;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.AppointmentTypeService;
import com.oakfield.clinic.svc.DocumentService;
import com.oakfield.clinic.svc.MessageService;
import com.oakfield.clinic.svc.PatientService;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Staff patient profile: demographics (editable by front desk/admin), recent
 * appointments, documents, message threads, and quick appointment booking.
 */
@Named("staffPatient")
@ViewScoped
public class StaffPatientProfileBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private PatientService patients;
    @Inject
    private AppointmentService appointments;
    @Inject
    private DocumentService documents;
    @Inject
    private MessageService messages;
    @Inject
    private ProviderService providers;
    @Inject
    private AppointmentTypeService appointmentTypes;

    private long id = -1;
    private Patient patient;

    private List<Appointment> patientAppointments = new ArrayList<>();
    private List<Document> patientDocuments = new ArrayList<>();
    private List<MessageThread> patientThreads = new ArrayList<>();

    // Demographics form (mirrors PatientService.PatientForm).
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String gender;
    private String phone;
    private String email;
    private String addressLine1;
    private String city;
    private String state;
    private String postalCode;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    private String insuranceGroupNumber;
    private Long primaryProviderId;
    private String medicalAlert;

    // Quick "add appointment" form.
    private Long newApptProviderId;
    private Long newApptTypeId;
    private LocalDate newApptDate;
    private List<LocalDateTime> newApptSlots = new ArrayList<>();
    private LocalDateTime newApptSlot;
    private String newApptReason;

    @PostConstruct
    public void load() {
        id = FacesUtil.paramLong("id", -1);
        patient = null;
        patientAppointments = new ArrayList<>();
        patientDocuments = new ArrayList<>();
        patientThreads = new ArrayList<>();
        if (id < 0) {
            return;
        }
        patient = patients.getForStaff(session.ctx(), id);
        fillForm();
        patientAppointments = appointments.search(session.ctx(),
                new AppointmentService.AppointmentFilter(null, null, null, id, null), 0, 5).items();
        patientDocuments = documents.search(session.ctx(),
                new DocumentService.DocFilter(id, null, null, null), 0, 5).items();
        patientThreads = messages.threadsForPatient(session.ctx(), id).stream().limit(5).toList();
    }

    private void fillForm() {
        firstName = patient.getFirstName();
        lastName = patient.getLastName();
        dateOfBirth = patient.getDateOfBirth();
        gender = patient.getGender() == null ? null : patient.getGender().name();
        phone = patient.getPhone();
        email = patient.getEmail();
        addressLine1 = patient.getAddressLine1();
        city = patient.getCity();
        state = patient.getState();
        postalCode = patient.getPostalCode();
        emergencyContactName = patient.getEmergencyContactName();
        emergencyContactPhone = patient.getEmergencyContactPhone();
        insuranceProvider = patient.getInsuranceProvider();
        insurancePolicyNumber = patient.getInsurancePolicyNumber();
        insuranceGroupNumber = patient.getInsuranceGroupNumber();
        primaryProviderId = patient.getPrimaryProvider() == null ? null : patient.getPrimaryProvider().getId();
        medicalAlert = patient.getMedicalAlert();
    }

    public void saveDemographics() {
        PatientService.PatientForm form = new PatientService.PatientForm(
                firstName, lastName, dateOfBirth, gender, phone, email, addressLine1, city, state,
                postalCode, emergencyContactName, emergencyContactPhone, insuranceProvider,
                insurancePolicyNumber, insuranceGroupNumber, primaryProviderId, medicalAlert);
        try {
            patients.updateDemographics(session.ctx(), id, form);
            FacesUtil.info("Saved", "Demographics updated.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not save", e.getMessage());
        }
    }

    public void loadNewApptSlots() {
        newApptSlots = new ArrayList<>();
        newApptSlot = null;
        if (newApptProviderId == null || newApptTypeId == null || newApptDate == null) {
            FacesUtil.error("Missing details", "Pick a provider, appointment type, and date first.");
            return;
        }
        try {
            newApptSlots = appointments.availableSlots(session.ctx(), newApptProviderId, newApptDate, newApptTypeId);
            if (newApptSlots.isEmpty()) {
                FacesUtil.warn("No availability", "No open slots on that date; try another day.");
            }
        } catch (ValidationException e) {
            FacesUtil.error("Could not load slots", e.getMessage());
        }
    }

    public void bookNewAppt() {
        if (newApptSlot == null) {
            FacesUtil.error("Add appointment", "Load slots and pick a time first.");
            return;
        }
        try {
            appointments.bookScheduled(session.ctx(), id, newApptProviderId, newApptTypeId,
                    newApptSlot, newApptReason, null);
            FacesUtil.info("Appointment added", "The appointment was scheduled.");
            newApptReason = null;
            newApptSlot = null;
            newApptSlots = new ArrayList<>();
            newApptDate = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not add appointment", e.getMessage());
        }
    }

    public List<Gender> getGenders() {
        return List.of(Gender.values());
    }

    public List<Provider> getProviders() {
        return providers.listActive();
    }

    public List<AppointmentType> getAppointmentTypes() {
        return appointmentTypes.listActive();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Patient getPatient() {
        return patient;
    }

    public List<Appointment> getPatientAppointments() {
        return patientAppointments;
    }

    public List<Document> getPatientDocuments() {
        return patientDocuments;
    }

    public List<MessageThread> getPatientThreads() {
        return patientThreads;
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }
    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = insuranceProvider; }
    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public void setInsurancePolicyNumber(String insurancePolicyNumber) { this.insurancePolicyNumber = insurancePolicyNumber; }
    public String getInsuranceGroupNumber() { return insuranceGroupNumber; }
    public void setInsuranceGroupNumber(String insuranceGroupNumber) { this.insuranceGroupNumber = insuranceGroupNumber; }
    public Long getPrimaryProviderId() { return primaryProviderId; }
    public void setPrimaryProviderId(Long primaryProviderId) { this.primaryProviderId = primaryProviderId; }
    public String getMedicalAlert() { return medicalAlert; }
    public void setMedicalAlert(String medicalAlert) { this.medicalAlert = medicalAlert; }

    public Long getNewApptProviderId() { return newApptProviderId; }
    public void setNewApptProviderId(Long newApptProviderId) { this.newApptProviderId = newApptProviderId; }
    public Long getNewApptTypeId() { return newApptTypeId; }
    public void setNewApptTypeId(Long newApptTypeId) { this.newApptTypeId = newApptTypeId; }
    public LocalDate getNewApptDate() { return newApptDate; }
    public void setNewApptDate(LocalDate newApptDate) { this.newApptDate = newApptDate; }
    public List<LocalDateTime> getNewApptSlots() { return newApptSlots; }
    public LocalDateTime getNewApptSlot() { return newApptSlot; }
    public void setNewApptSlot(LocalDateTime newApptSlot) { this.newApptSlot = newApptSlot; }
    public String getNewApptReason() { return newApptReason; }
    public void setNewApptReason(String newApptReason) { this.newApptReason = newApptReason; }
}
