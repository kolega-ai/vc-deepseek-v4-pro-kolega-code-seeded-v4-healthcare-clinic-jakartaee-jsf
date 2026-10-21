package com.oakfield.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prescription")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    @Column(nullable = false, length = 120)
    private String dosage;

    @Column(nullable = false, length = 120)
    private String frequency;

    @Column(length = 1000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status = PrescriptionStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "refill_status", nullable = false, length = 20)
    private RefillStatus refillStatus = RefillStatus.NONE;

    @Column(name = "refill_requested_at")
    private LocalDateTime refillRequestedAt;

    @Column(name = "refill_request_note", length = 1000)
    private String refillRequestNote;

    @Column(name = "refill_response_note", length = 1000)
    private String refillResponseNote;

    @Column(name = "refill_decided_by")
    private Long refillDecidedBy;

    @Column(name = "refill_decided_at")
    private LocalDateTime refillDecidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refill_transmit_status", length = 20)
    private RefillTransmitStatus refillTransmitStatus;

    @Column(name = "refill_transmit_ref", length = 80)
    private String refillTransmitRef;

    @Column(name = "refills_remaining", nullable = false)
    private int refillsRemaining;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public String getMedicationName() { return medicationName; }
    public void setMedicationName(String medicationName) { this.medicationName = medicationName; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public PrescriptionStatus getStatus() { return status; }
    public void setStatus(PrescriptionStatus status) { this.status = status; }
    public RefillStatus getRefillStatus() { return refillStatus; }
    public void setRefillStatus(RefillStatus refillStatus) { this.refillStatus = refillStatus; }
    public LocalDateTime getRefillRequestedAt() { return refillRequestedAt; }
    public void setRefillRequestedAt(LocalDateTime refillRequestedAt) { this.refillRequestedAt = refillRequestedAt; }
    public String getRefillRequestNote() { return refillRequestNote; }
    public void setRefillRequestNote(String refillRequestNote) { this.refillRequestNote = refillRequestNote; }
    public String getRefillResponseNote() { return refillResponseNote; }
    public void setRefillResponseNote(String refillResponseNote) { this.refillResponseNote = refillResponseNote; }
    public Long getRefillDecidedBy() { return refillDecidedBy; }
    public void setRefillDecidedBy(Long refillDecidedBy) { this.refillDecidedBy = refillDecidedBy; }
    public LocalDateTime getRefillDecidedAt() { return refillDecidedAt; }
    public void setRefillDecidedAt(LocalDateTime refillDecidedAt) { this.refillDecidedAt = refillDecidedAt; }
    public RefillTransmitStatus getRefillTransmitStatus() { return refillTransmitStatus; }
    public void setRefillTransmitStatus(RefillTransmitStatus refillTransmitStatus) { this.refillTransmitStatus = refillTransmitStatus; }
    public String getRefillTransmitRef() { return refillTransmitRef; }
    public void setRefillTransmitRef(String refillTransmitRef) { this.refillTransmitRef = refillTransmitRef; }
    public int getRefillsRemaining() { return refillsRemaining; }
    public void setRefillsRemaining(int refillsRemaining) { this.refillsRemaining = refillsRemaining; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
