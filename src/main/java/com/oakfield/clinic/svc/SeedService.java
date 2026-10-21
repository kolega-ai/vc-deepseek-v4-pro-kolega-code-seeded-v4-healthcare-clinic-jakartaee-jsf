package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.decision.PasswordHasher;
import com.oakfield.clinic.model.*;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Demo seed data, executed once on first boot. All seeded workflow states are
 * produced through the real service transitions (and therefore the state
 * machines and the audit log) - never by direct insertion of an
 * otherwise-unreachable status. Historical rows (past appointments) are
 * inserted in their creation state and then moved through the same stated
 * actions a live user would perform.
 */
@Singleton
@Startup
@DependsOn("MigrationService")
public class SeedService {

    private static final Logger LOG = Logger.getLogger(SeedService.class);

    private static final String PW = "PatientPortal2026!";

    @PersistenceContext
    private EntityManager em;

    @EJB
    private DecisionService decision;
    @EJB
    private AuditService audit;
    @EJB
    private AppointmentService appointments;
    @EJB
    private VisitService visits;
    @EJB
    private PrescriptionService prescriptions;
    @EJB
    private ProviderService providers;
    @EJB
    private NotificationService notifications;
    @EJB
    private SettingsService settings;
    @EJB
    private IntegrationService integrations;
    @EJB
    private ScheduleService schedules;
    @EJB
    private DocumentService documents;
    @EJB
    private OutboxService outbox;

    private User admin, vanceU, chenU, nairU, okaforU, romeroU, maya, jorge, danielU, graceU, omarU;
    private Provider vance, chen, nair, okafor, romero;
    private Patient daniel, grace, omar, sofia, ethan, priyaR, lucas, hannah, margaret, tomas, aisha, walter;

    @PostConstruct
    public void seed() {
        try {
            Long users = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
            if (users != null && users > 0) {
                LOG.info("Seed skipped: users already exist.");
                return;
            }
            run();
            LOG.info("Demo seed complete: users, patients, providers, appointments, visits, "
                    + "prescriptions, messages, documents, notifications, webhook.");
        } catch (Exception e) {
            LOG.error("Seed failed (application will run with empty data): " + e.getMessage(), e);
        }
    }

    private void run() {
        seedUsers();
        seedProvidersAndSchedules();
        seedPatients();
        seedAppointments();
        seedVisits();
        seedPrescriptions();
        seedDocuments();
        seedMessages();
        seedNotificationsAndAudit();
        seedWebhook();
    }

    // ------------------------------------------------------------ actors

    private void seedUsers() {
        admin = user("admin@oakfield.local", "Alice Whitman", AppRole.CLINIC_ADMIN, "(555) 014-2201");
        vanceU = user("clinician@oakfield.local", "Dr. Eleanor Vance", AppRole.CLINICIAN, "(555) 014-2210");
        chenU = user("chen@oakfield.local", "Dr. Marcus Chen", AppRole.CLINICIAN, "(555) 014-2211");
        nairU = user("nair@oakfield.local", "Dr. Priya Nair", AppRole.CLINICIAN, "(555) 014-2212");
        okaforU = user("okafor@oakfield.local", "Dr. Samuel Okafor", AppRole.CLINICIAN, "(555) 014-2213");
        romeroU = user("romero@oakfield.local", "Alicia Romero, NP", AppRole.CLINICIAN, "(555) 014-2214");
        maya = user("frontdesk@oakfield.local", "Maya Torres", AppRole.FRONT_DESK, "(555) 014-2202");
        jorge = user("jorge@oakfield.local", "Jorge Ramirez", AppRole.FRONT_DESK, "(555) 014-2203");
        danielU = user("patient@oakfield.local", "Daniel Reyes", AppRole.PATIENT, "(555) 019-3412");
        graceU = user("grace.lin@example.test", "Grace Lin", AppRole.PATIENT, "(555) 019-8890");
        omarU = user("omar.haddad@example.test", "Omar Haddad", AppRole.PATIENT, "(555) 019-2217");
    }

    private User user(String email, String fullName, AppRole role, String phone) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(fullName);
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setPhone(phone);
        u.setPasswordHash(PasswordHasher.hash(PW));
        u.setPasswordChangedAt(LocalDateTime.now());
        em.persist(u);
        return u;
    }

    private void seedProvidersAndSchedules() {
        vance = provider(vanceU, "Eleanor", "Vance", "MD", "Family Medicine", "1982746551", "(555) 014-2210");
        chen = provider(chenU, "Marcus", "Chen", "MD", "Internal Medicine", "1457368920", "(555) 014-2211");
        nair = provider(nairU, "Priya", "Nair", "MD", "Pediatrics", "1629084375", "(555) 014-2212");
        okafor = provider(okaforU, "Samuel", "Okafor", "MD", "Family Medicine", "1745239086", "(555) 014-2213");
        romero = provider(romeroU, "Alicia", "Romero", "NP", "Nurse Practitioner", "1837465210", "(555) 014-2214");
        for (Provider p : List.of(vance, chen, nair, okafor, romero)) {
            for (int dow = 1; dow <= 5; dow++) {
                schedules.saveSchedule(DecisionContext.system("seed"), p.getId(), dow, LocalTime.of(9, 0), LocalTime.of(17, 0));
            }
        }
        schedules.saveSchedule(DecisionContext.system("seed"), vance.getId(), 6, LocalTime.of(9, 0), LocalTime.of(13, 0));
    }

    private Provider provider(User u, String first, String last, String title, String specialty,
                              String npi, String phone) {
        Provider p = new Provider();
        p.setUser(u);
        p.setFirstName(first);
        p.setLastName(last);
        p.setTitle(title);
        p.setSpecialty(specialty);
        p.setNpi(npi);
        p.setPhone(phone);
        p.setEmail(u.getEmail());
        p.setActive(true);
        em.persist(p);
        return p;
    }

    private void seedPatients() {
        daniel = patient(danielU, "Daniel", "Reyes", "1988-03-14", "M", "(555) 019-3412",
                "14 Cedar Court", "Oakfield", "CA", "90210", "Northgate Health Plan", "NGP-88213", "GRP-2201",
                "Lucia Reyes", "(555) 019-3413", vance, null);
        grace = patient(graceU, "Grace", "Lin", "1992-07-02", "F", "(555) 019-8890",
                "77 Harbor Way", "Oakfield", "CA", "90211", "Blue Ridge Mutual", "BRM-55019", "GRP-1144",
                "Wei Lin", "(555) 019-8891", vance, null);
        omar = patient(omarU, "Omar", "Haddad", "1979-11-21", "M", "(555) 019-2217",
                "230 Ash Street", "Oakfield", "CA", "90212", "Northgate Health Plan", "NGP-90034", "GRP-2201",
                "Nadia Haddad", "(555) 019-2218", chen, null);
        sofia = patient(null, "Sofia", "Marchetti", "2004-01-30", "F", "(555) 017-5521",
                "8 Birch Avenue", "Oakfield", "CA", "90210", null, null, null,
                "Elena Marchetti", "(555) 017-5522", nair, null);
        ethan = patient(null, "Ethan", "Brooks", "2016-05-12", "M", "(555) 017-6620",
                "451 Pine Road", "Oakfield", "CA", "90213", "Blue Ridge Mutual", "BRM-66112", "GRP-1144",
                "Dana Brooks", "(555) 017-6621", nair, "Peanut allergy");
        priyaR = patient(null, "Priya", "Raman", "1958-09-08", "F", "(555) 016-7781",
                "12 Willow Circle", "Oakfield", "CA", "90212", "Medicare Demo", "MD-77120", "GRP-MED",
                "Arun Raman", "(555) 016-7782", chen, null);
        lucas = patient(null, "Lucas", "Ferreira", "1995-12-01", "M", "(555) 018-9921",
                "900 Maple Lane", "Oakfield", "CA", "90211", "Northgate Health Plan", "NGP-71002", "GRP-2201",
                "Beatriz Ferreira", "(555) 018-9922", okafor, null);
        hannah = patient(null, "Hannah", "Kim", "1983-06-19", "F", "(555) 018-1120",
                "61 Spruce Court", "Oakfield", "CA", "90214", "Blue Ridge Mutual", "BRM-44901", "GRP-1144",
                "David Kim", "(555) 018-1121", okafor, null);
        margaret = patient(null, "Margaret", "O'Brien", "1947-02-25", "F", "(555) 015-3344",
                "3 Rose Terrace", "Oakfield", "CA", "90210", "Medicare Demo", "MD-81210", "GRP-MED",
                "Sean O'Brien", "(555) 015-3345", vance, "Uses a hearing aid");
        tomas = patient(null, "Tomas", "Alvarez", "2008-10-03", "M", "(555) 017-1100",
                "198 Juniper Drive", "Oakfield", "CA", "90213", null, null, null,
                "Carmen Alvarez", "(555) 017-1101", romero, null);
        aisha = patient(null, "Aisha", "Bello", "1990-04-27", "F", "(555) 018-2209",
                "55 Alder Street", "Oakfield", "CA", "90212", "Northgate Health Plan", "NGP-77410", "GRP-2201",
                "Tunde Bello", "(555) 018-2210", romero, null);
        walter = patient(null, "Walter", "Nguyen", "1969-08-15", "M", "(555) 016-4400",
                "301 Elm Boulevard", "Oakfield", "CA", "90214", "Medicare Demo", "MD-66331", "GRP-MED",
                "Helen Nguyen", "(555) 016-4401", chen, null);
    }

    private Patient patient(User u, String first, String last, String dob, String gender, String phone,
                            String address, String city, String state, String zip,
                            String insurer, String policy, String group, String ecName, String ecPhone,
                            Provider primary, String alert) {
        Patient p = new Patient();
        p.setUser(u);
        p.setFirstName(first);
        p.setLastName(last);
        p.setDateOfBirth(LocalDate.parse(dob));
        p.setGender(PatientService.parseGender(gender));
        p.setPhone(phone);
        p.setEmail(u == null ? (first.toLowerCase() + "." + last.toLowerCase() + "@example.test") : u.getEmail());
        p.setAddressLine1(address);
        p.setCity(city);
        p.setState(state);
        p.setPostalCode(zip);
        p.setInsuranceProvider(insurer);
        p.setInsurancePolicyNumber(policy);
        p.setInsuranceGroupNumber(group);
        p.setEmergencyContactName(ecName);
        p.setEmergencyContactPhone(ecPhone);
        p.setPrimaryProvider(primary);
        p.setMedicalAlert(alert);
        p.setActive(true);
        em.persist(p);
        return p;
    }

    // ------------------------------------------------------------ appointments

    private void seedAppointments() {
        AppointmentType followUp = type("FOLLOW_UP");
        AppointmentType sick = type("SICK");
        AppointmentType annual = type("ANNUAL");
        AppointmentType telehealth = type("TELEHEALTH");
        AppointmentType vaccination = type("VACCINATION");
        AppointmentType newPatient = type("NEW_PATIENT");

        // Completed history (inserted in creation state, then completed via the stated action)
        completed(vance, daniel, followUp, at(daysAgo(21), 10, 0), "Blood pressure follow-up");
        completed(vance, grace, annual, at(daysAgo(18), 9, 0), "Annual physical");
        completed(chen, omar, followUp, at(daysAgo(16), 14, 30), "Medication review");
        completed(nair, ethan, sick, at(daysAgo(15), 11, 0), "Cough and fever");
        completed(chen, priyaR, annual, at(daysAgo(13), 9, 30), "Annual wellness");
        completed(okafor, lucas, followUp, at(daysAgo(12), 15, 0), "Knee pain check");
        completed(vance, margaret, newPatient, at(daysAgo(10), 10, 30), "New patient visit");
        completed(romero, tomas, vaccination, at(daysAgo(9), 16, 0), "School vaccinations");
        completed(nair, ethan, followUp, at(daysAgo(8), 11, 30), "Recovery check");
        completed(chen, walter, annual, at(daysAgo(7), 9, 0), "Annual wellness");
        completed(okafor, hannah, sick, at(daysAgo(4), 13, 0), "Sinus infection");
        completed(vance, daniel, sick, at(daysAgo(3), 15, 30), "Sore throat");

        // No-show and cancellations (past, produced via the stated transitions)
        Appointment noShow = insertAppointment(vance, grace, telehealth, at(daysAgo(2), 10, 0), "Telehealth check-in", "SCHEDULED");
        appointments.markNoShow(mayaCtx(), noShow.getId(), "Patient did not join the call.");
        Appointment cancelled1 = insertAppointment(chen, omar, followUp, at(daysAgo(1), 14, 0), "Lab results review", "SCHEDULED");
        appointments.cancel(mayaCtx(), cancelled1.getId(), "Patient called to reschedule.");
        Appointment cancelled2 = insertAppointment(okafor, hannah, followUp, at(daysAgo(1), 9, 30), "Migraine review", "SCHEDULED");
        appointments.cancel(mayaCtx(), cancelled2.getId(), "Provider out sick.");
        Appointment cancelled3 = insertAppointment(nair, sofia, sick, at(daysAgo(6), 10, 0), "Rash", "SCHEDULED");
        appointments.cancel(mayaCtx(), cancelled3.getId(), "Patient no longer needed the visit.");

        // Today: a checked-in patient (front-desk action, recorded as such), a few
        // live bookings IF the clinic still has open slots today, and a pending
        // walk-in request. All times are chosen so no service call can fail
        // validation (the seed must never leave a partially-rolled-back state).
        Appointment checkedInToday = insertAppointment(chen, walter, followUp,
                at(today(), 9, 0), "BP medication review", "SCHEDULED");
        appointments.checkIn(mayaCtx(), checkedInToday.getId());
        LocalTime[] todaySlots = {LocalTime.of(10, 0), LocalTime.of(10, 30), LocalTime.of(11, 0), LocalTime.of(11, 30)};
        int slotIdx = 0;
        if (LocalDateTime.now().isBefore(at(today(), 10, 0))) {
            bookIfFuture(slot(todaySlots[slotIdx++]), daniel, vance, followUp, "Follow-up on new medication");
            bookIfFuture(slot(todaySlots[slotIdx++]), grace, vance, telehealth, "Virtual check-in");
            bookIfFuture(slot(todaySlots[slotIdx++]), tomas, romero, vaccination, "Second dose");
        }
        Appointment lateRequest = new Appointment();
        {
            lateRequest.setPatient(lucas);
            lateRequest.setProvider(okafor);
            lateRequest.setType(sick);
            lateRequest.setStartTime(LocalDateTime.now().plusHours(5).withMinute(0));
            lateRequest.setEndTime(lateRequest.getStartTime().plusMinutes(sick.getDurationMinutes()));
            lateRequest.setStatus(AppointmentStatus.REQUESTED);
            lateRequest.setReason("Back pain flare-up");
            lateRequest.setCreatedByUserId(lucas.getUser() == null ? null : lucas.getUser().getId());
            lateRequest.setCreatedAt(LocalDateTime.now().minusHours(2));
            lateRequest.setUpdatedAt(LocalDateTime.now().minusHours(2));
            em.persist(lateRequest);
            audit.record(mayaCtx(), AuditService.APPOINTMENT_BOOKED, "APPOINTMENT", String.valueOf(lateRequest.getId()),
                    null, Json.obj("status", "REQUESTED", "start", String.valueOf(lateRequest.getStartTime()),
                            "provider", okafor.fullName(), "patient", lucas.fullName()));
        }

        // Future bookings
        appointments.bookScheduled(mayaCtx(), daniel.getId(), vance.getId(), followUp.getId(),
                at(nextWorkday(2), 9, 0), "Medication check", null);
        appointments.bookScheduled(mayaCtx(), priyaR.getId(), chen.getId(), annual.getId(),
                at(nextWorkday(3), 10, 30), "Diabetes review", null);
        appointments.bookScheduled(mayaCtx(), ethan.getId(), nair.getId(), followUp.getId(),
                at(nextWorkday(4), 15, 0), "Allergy follow-up", null);
        appointments.bookScheduled(mayaCtx(), margaret.getId(), vance.getId(), followUp.getId(),
                at(nextWorkday(5), 11, 0), "Medication tolerance check", null);
        appointments.bookScheduled(mayaCtx(), aisha.getId(), romero.getId(), newPatient.getId(),
                at(nextWorkday(6), 13, 30), "New patient intake", null);
        appointments.bookScheduled(mayaCtx(), hannah.getId(), okafor.getId(), annual.getId(),
                at(nextWorkday(7), 9, 0), "Annual physical", null);
        appointments.bookScheduled(mayaCtx(), lucas.getId(), okafor.getId(), followUp.getId(),
                at(nextWorkday(8), 13, 0), "Knee recheck", null);
        appointments.bookScheduled(mayaCtx(), daniel.getId(), vance.getId(), telehealth.getId(),
                at(nextWorkday(14), 16, 0), "Remote follow-up", null);
        appointments.bookScheduled(mayaCtx(), sofia.getId(), nair.getId(), annual.getId(),
                at(nextWorkday(12), 10, 0), "Sports clearance physical", null);

        // Patient self-service booking requests (awaiting confirmation)
        appointments.bookRequested(graceCtx(), grace.getId(), vance.getId(), followUp.getId(),
                at(nextWorkday(9), 9, 30), "Thyroid recheck", null);
        appointments.bookRequested(omarCtx(), omar.getId(), chen.getId(), followUp.getId(),
                at(nextWorkday(10), 14, 0), "Cholesterol follow-up", null);
        // One more routine booking keeps the board above 30 even when late-evening boots
        // skip the intra-day "today" slots.
        appointments.bookScheduled(mayaCtx(), hannah.getId(), okafor.getId(), followUp.getId(),
                at(nextWorkday(16), 10, 30), "Medication review", null);
    }

    private void completed(Provider provider, Patient patient, AppointmentType type, LocalDateTime start, String reason) {
        Appointment a = insertAppointment(provider, patient, type, start, reason, "SCHEDULED");
        appointments.complete(mayaCtx(), a.getId());
    }

    /** Book at the given time only when it is a valid future slot today (no failure path). */
    private void bookIfFuture(LocalDateTime start, Patient patient, Provider provider, AppointmentType type, String reason) {
        if (start == null || !start.isAfter(LocalDateTime.now())) return;
        try {
            appointments.bookScheduled(mayaCtx(), patient.getId(), provider.getId(), type.getId(), start, reason, null);
        } catch (Exception e) {
            LOG.warnf("Seed booking skipped (%s): %s", reason, e.getMessage());
            throw e; // never swallow inside a transaction: let the seed fail loudly instead of corrupting state
        }
    }

    private static LocalDateTime slot(LocalTime time) {
        return LocalDate.now().atTime(time);
    }

    private Appointment insertAppointment(Provider provider, Patient patient, AppointmentType type,
                                          LocalDateTime start, String reason, String status) {
        Appointment a = new Appointment();
        a.setPatient(patient);
        a.setProvider(provider);
        a.setType(type);
        a.setStartTime(start);
        a.setEndTime(start.plusMinutes(type.getDurationMinutes()));
        a.setStatus(AppointmentStatus.valueOf(status));
        a.setReason(reason);
        a.setCreatedByUserId(maya.getId());
        a.setCreatedAt(start.minusDays(7));
        a.setUpdatedAt(start.minusDays(7));
        em.persist(a);
        audit.record(mayaCtx(), AuditService.APPOINTMENT_BOOKED, "APPOINTMENT", String.valueOf(a.getId()),
                null, Json.obj("status", status, "start", String.valueOf(start),
                        "provider", provider.fullName(), "patient", patient.fullName()));
        return a;
    }

    private AppointmentType type(String code) {
        return em.createQuery("select t from AppointmentType t where t.code = :c", AppointmentType.class)
                .setParameter("c", code).getSingleResult();
    }

    // ------------------------------------------------------------ visits

    private void seedVisits() {
        visit(vance, daniel, 21, "Hypertension (I10). Blood pressure improving on current therapy.",
                "Continue Lisinopril 10 mg daily. Home BP log twice a week.", "Return in 3 months for BP recheck.",
                "Patient reports occasional lightheadedness in the morning - monitor.", true);
        visit(vance, grace, 18, "Routine annual exam. No acute findings.",
                "Maintain current diet and exercise plan.", "Annual labs ordered; return in 12 months.",
                null, true);
        visit(chen, omar, 16, "Dyslipidemia (E78.5). LDL elevated.",
                "Continue Atorvastatin 20 mg. Repeat lipid panel in 6 weeks.", "Schedule labs before next visit.",
                "Discussed dietary changes; patient motivated.", false);
        visit(nair, ethan, 15, "Acute viral upper respiratory infection (J06.9).",
                "Rest, fluids, acetaminophen as needed for fever.", "Return if fever persists beyond 3 days.",
                null, true);
        visit(chen, priyaR, 13, "Type 2 diabetes, well controlled (E11.9).",
                "Continue metformin; A1C stable at 6.8%.", "Retinal screening recommended.",
                null, true);
        visit(okafor, lucas, 12, "Patellofemoral pain syndrome (M22.2).",
                "PT exercises provided; ice after activity.", "Recheck in 4 weeks.",
                "MRI not indicated at this stage.", false);
        visit(vance, margaret, 10, "New patient baseline. Osteoarthritis (M19.90).",
                "Home safety review done; consider assistive device for stairs.",
                "Follow-up in 6 weeks.", "Bring medication list next visit.", true);
        visit(nair, ethan, 8, "Resolving viral URI. Lungs clear.",
                "Return to school; no restrictions.", "None required.",
                null, false);
        visit(chen, walter, 7, "Essential hypertension (I10). Medications reviewed.",
                "Losartan dose maintained.", "Annual labs.",
                "Family history of stroke noted.", true);
        visit(okafor, hannah, 4, "Acute sinusitis (J01.90).",
                "Amoxicillin-clavulanate prescribed; saline rinses.", "Return if symptoms persist > 10 days.",
                "Sensitive to strong scents - prescribe scent-free instructions.", false);
        visit(vance, daniel, 3, "Acute pharyngitis (J02.9). Rapid strep negative.",
                "Symptomatic care; warm salt gargles.", "Return if difficulty swallowing develops.",
                null, true);
    }

    private void visit(Provider provider, Patient patient, int daysAgo, String diagnosis, String care,
                       String followUp, String notes, boolean release) {
        Appointment a = em.createQuery(
                        "select a from Appointment a where a.provider.id = :pid and a.patient.id = :pat " +
                        "and a.startTime >= :from and a.startTime < :to and a.status = :st", Appointment.class)
                .setParameter("pid", provider.getId())
                .setParameter("pat", patient.getId())
                .setParameter("from", daysAgo(daysAgo).atStartOfDay())
                .setParameter("to", daysAgo(daysAgo).plusDays(1).atStartOfDay())
                .setParameter("st", AppointmentStatus.COMPLETED)
                .getResultStream().findFirst().orElse(null);
        if (a == null) return;
        VisitSummary v = visits.createDraft(providerCtx(provider),
                a.getId(), new VisitService.VisitForm(diagnosis, care, followUp, notes));
        if (release) {
            visits.release(providerCtx(provider), v.getId());
        }
    }

    // ------------------------------------------------------------ prescriptions

    private void seedPrescriptions() {
        Prescription lisinopril = rx(vance, daniel, "Lisinopril", "10 mg", "Once daily", "Take in the morning.", 3, null);
        rx(vance, daniel, "Albuterol inhaler", "90 mcg", "As needed", "2 puffs every 4-6 hours for wheezing.", 2, null);
        rx(chen, omar, "Atorvastatin", "20 mg", "Once daily at bedtime", "Avoid grapefruit juice.", 2, null);
        rx(chen, omar, "Omeprazole", "20 mg", "Once daily", "Take 30 minutes before breakfast.", 1, null);
        rx(vance, grace, "Metformin", "500 mg", "Twice daily with meals", null, 2, null);
        rx(vance, grace, "Levothyroxine", "50 mcg", "Once daily", "Take on an empty stomach.", 2, null);
        rx(vance, margaret, "Metoprolol", "25 mg", "Twice daily", "Check pulse before dosing.", 2, null);
        rx(chen, priyaR, "Insulin glargine", "10 units", "Once daily at bedtime", "Rotate injection sites.", 1, null);
        rx(okafor, hannah, "Sertraline", "50 mg", "Once daily", null, 2, null);
        rx(okafor, lucas, "Ibuprofen", "600 mg", "Every 8 hours with food", "Short course only.", 0, null);
        rx(chen, walter, "Losartan", "50 mg", "Once daily", null, 2, null);
        rx(okafor, hannah, "Amoxicillin-clavulanate", "875/125 mg", "Twice daily", "Finish the full 10-day course.", 0, null);

        // Refill workflow, exercised through the real transitions.
        // 1) Approved + transmitted (Daniel, via the demo patient account)
        Prescription albuterol = rxBy(daniel, "Albuterol inhaler");
        prescriptions.requestRefill(danielCtx(), albuterol.getId(), "Almost out; using daily for allergies.");
        prescriptions.approveRefill(vanceCtx(), albuterol.getId(), "Approved; 1 refill transmitted to the pharmacy.");
        // 2) Needs info (Grace)
        Prescription metformin = rxBy(grace, "Metformin");
        prescriptions.requestRefill(graceCtx(), metformin.getId(), "Running low.");
        prescriptions.needsInfoRefill(vanceCtx(), metformin.getId(), "Please share your most recent fasting glucose readings.");
        // 3) Denied (Omar)
        Prescription omeprazole = rxBy(omar, "Omeprazole");
        prescriptions.requestRefill(omarCtx(), omeprazole.getId(), "Refill request.");
        prescriptions.denyRefill(chenCtx(), omeprazole.getId(), "Please book a follow-up visit before we renew this medication.");
        // 4) Pending in queue (Omar)
        Prescription atorvastatin = rxBy(omar, "Atorvastatin");
        prescriptions.requestRefill(omarCtx(), atorvastatin.getId(), "Out of refills at the pharmacy.");
        // 5) Cancelled by the patient (Daniel)
        Prescription lisinoprilR = rxBy(daniel, "Lisinopril");
        prescriptions.requestRefill(danielCtx(), lisinoprilR.getId(), "Refill request.");
        prescriptions.cancelRefill(danielCtx(), lisinoprilR.getId());
        // 6) Approved (Margaret called the clinic; staff filed the request on her behalf)
        Prescription metoprolol = rxBy(margaret, "Metoprolol");
        prescriptions.requestRefillOnBehalf(vanceCtx(), metoprolol.getId(),
                "Patient called the front desk to request a refill.");
        prescriptions.approveRefill(vanceCtx(), metoprolol.getId(), "Approved per protocol.");
    }

    private Prescription rx(Provider provider, Patient patient, String medication, String dosage, String frequency,
                            String instructions, int refills, Appointment appointment) {
        return prescriptions.create(providerCtx(provider), patient.getId(),
                new PrescriptionService.RxForm(medication, dosage, frequency, instructions, refills,
                        appointment == null ? null : appointment.getId()));
    }

    private Prescription rxBy(Patient patient, String medication) {
        return em.createQuery(
                        "select r from Prescription r where r.patient.id = :pid and r.medicationName = :m", Prescription.class)
                .setParameter("pid", patient.getId())
                .setParameter("m", medication)
                .getResultStream().findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ documents

    private void seedDocuments() {
        // FORMs: pre-visit paperwork, several awaiting verification, one rejected, one archived
        doc(daniel, DocumentCategory.FORM, "Insurance card (front and back)", "Pre-visit insurance verification",
                d -> documents.verify(mayaCtx(), d.getId(), "Matches the policy on file."));
        doc(daniel, DocumentCategory.FORM, "New patient intake form", "Completed intake questionnaire", null);
        doc(grace, DocumentCategory.FORM, "Insurance card", "Blue Ridge Mutual card",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(grace, DocumentCategory.FORM, "Consent for treatment", "Signed consent",
                d -> documents.reject(mayaCtx(), d.getId(), "Signature is cut off at the bottom - please re-upload a full-page scan."));
        doc(omar, DocumentCategory.FORM, "Consent for treatment", "Signed consent",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(margaret, DocumentCategory.FORM, "Advance directive", "Healthcare power of attorney",
                d -> documents.verify(mayaCtx(), d.getId(), "Notarized copy on file."));
        doc(aisha, DocumentCategory.FORM, "Insurance card", "New patient insurance verification", null);
        doc(tomas, DocumentCategory.FORM, "School physical form", "For the fall sports season", d -> {
            documents.verify(mayaCtx(), d.getId(), null);
            documents.archive(mayaCtx(), d.getId());
        });
        doc(priyaR, DocumentCategory.FORM, "Consent for treatment", "Signed consent",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(hannah, DocumentCategory.FORM, "Insurance card", "Blue Ridge Mutual card", null);
        doc(sofia, DocumentCategory.FORM, "Consent for treatment", "Signed consent",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        // CLINICAL documents (uploaded by staff)
        doc(daniel, DocumentCategory.CLINICAL, "CBC results", "LabCorp order 22110",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(daniel, DocumentCategory.CLINICAL, "Chest X-ray report", "Radiology report",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(grace, DocumentCategory.CLINICAL, "A1C lab results", "Quarterly diabetes panel",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(omar, DocumentCategory.CLINICAL, "Lipid panel results", "Fasting lipid panel",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(margaret, DocumentCategory.CLINICAL, "ECG report", "12-lead electrocardiogram",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(walter, DocumentCategory.CLINICAL, "Home blood pressure log", "Patient-provided 30-day log",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(ethan, DocumentCategory.CLINICAL, "Vaccination record", "Updated immunization history",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(lucas, DocumentCategory.CLINICAL, "Knee MRI report", "Imaging report from Oakfield Imaging",
                d -> documents.verify(mayaCtx(), d.getId(), null));
        doc(grace, DocumentCategory.CLINICAL, "Current medication list", "Reconciled medication list",
                d -> documents.verify(mayaCtx(), d.getId(), null));
    }

    private void doc(Patient patient, DocumentCategory category, String title, String description,
                     java.util.function.Consumer<Document> review) {
        try {
            String storedName = java.util.UUID.randomUUID().toString().replace("-", "") + ".pdf";
            Path target = FileStore.resolve(FileStore.Area.UPLOADS, storedName);
            byte[] bytes = category == DocumentCategory.CLINICAL
                    ? ("OAKFIELD FAMILY CLINIC - " + title + "\nPatient: " + patient.fullName()
                        + "\n" + description + "\nThis is a seeded mock document for the local demo.\n")
                        .getBytes(StandardCharsets.UTF_8)
                    : mockPdf(title, patient.fullName(), description);
            Files.write(target, bytes);
            Document d = new Document();
            d.setPatient(patient);
            d.setUploadedBy(category == DocumentCategory.FORM ? patient.getUser() == null ? maya.getId() : patient.getUser().getId() : maya.getId());
            d.setCategory(category);
            d.setTitle(title);
            d.setDescription(description);
            d.setOriginalName(title.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".pdf");
            d.setStoredName(storedName);
            d.setContentType("application/pdf");
            d.setSizeBytes(bytes.length);
            d.setSha256(DocumentService.sha256Hex(bytes));
            d.setStatus(DocumentStatus.SUBMITTED);
            d.setUploadedAt(LocalDateTime.now().minusDays(2));
            em.persist(d);
            audit.record(mayaCtx(), AuditService.DOC_UPLOADED, "DOCUMENT", String.valueOf(d.getId()),
                    null, Json.obj("category", category.name(), "title", title, "patient", String.valueOf(patient.getId())));
            if (review != null) {
                review.accept(d); // failures propagate: the seed must not continue inside a broken transaction
            }
        } catch (Exception e) {
            LOG.warnf("Could not seed document '%s': %s", title, e.getMessage());
        }
    }

    /** Minimal but structurally valid single-page PDF with correct xref offsets. */
    private byte[] mockPdf(String title, String patient, String description) {
        String text = "Oakfield Family Clinic - " + title + " - Patient: " + patient;
        String content = "BT /F1 14 Tf 72 720 Td (" + text.replace("(", "\\(").replace(")", "\\)") + ") Tj ET";
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[5];
        offsets[0] = pdf.length();
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        offsets[1] = pdf.length();
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        offsets[2] = pdf.length();
        pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
                + "/Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n");
        offsets[3] = pdf.length();
        pdf.append("4 0 obj\n<< /Length ").append(content.length()).append(" >>\nstream\n")
           .append(content).append("\nendstream\nendobj\n");
        offsets[4] = pdf.length();
        pdf.append("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        int xref = pdf.length();
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 0; i < 5; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    // ------------------------------------------------------------ messages

    private void seedMessages() {
        thread(daniel, "Question about my prescription", vance,
                msg(danielU, "PATIENT_VISIBLE", "Hi - I've been on the new blood pressure medication for a week and I feel a little lightheaded in the mornings. Should I be concerned?"),
                msg(vanceU, "PATIENT_VISIBLE", "Thanks for reaching out. Mild lightheadedness can happen while your body adjusts. Keep taking it with breakfast, avoid standing up too quickly, and log your morning readings. If it persists more than another week, we'll adjust the dose."),
                msg(vanceU, "INTERNAL", "Reyes: monitor for orthostatic symptoms. Review home BP log at next visit."));
        thread(grace, "Insurance update", vance,
                msg(graceU, "PATIENT_VISIBLE", "I switched to Blue Ridge Mutual this month - should I bring anything to my next visit?"),
                msg(maya, "PATIENT_VISIBLE", "Thanks for letting us know, Grace! Just bring your new insurance card and a photo ID. I've noted the change on your file."));
        thread(omar, "Test results", chen,
                msg(omarU, "PATIENT_VISIBLE", "Good afternoon - have my latest lipid results come back? It's been about two weeks."),
                msg(chenU, "PATIENT_VISIBLE", "They arrived this morning. Your LDL is down from last time but still above target. I'll review everything with you at your follow-up."),
                msg(chenU, "INTERNAL", "Haddad: counsel on dietary adherence; consider statin dose adjustment if LDL unchanged at next draw."));
        thread(margaret, "Refill timing", vance,
                msg(maya, "PATIENT_VISIBLE", "Hello Mrs. O'Brien, this is Maya from the front desk. Just a note that your metoprolol refill request has been received and is with Dr. Vance."));
        thread(lucas, "Need an appointment", okafor,
                msg(maya, "PATIENT_VISIBLE", "Hi Lucas, your follow-up for the knee is confirmed. Please arrive 10 minutes early to update your paperwork."),
                msg(maya, "INTERNAL", "Ferreira: bring the home exercise log. Dr. Okafor wants to see week 3 and 4 entries."));
        thread(hannah, "Billing question", okafor,
                msg(maya, "PATIENT_VISIBLE", "Hi Hannah - I can see the claim for your last visit is still processing with Blue Ridge. I'll follow up and let you know if anything is needed from you."),
                msg(jorge, "INTERNAL", "Kim claim #BRM-44901 - flagged for review. Do not call patient yet."));
        thread(daniel, "Follow-up on visit", vance,
                msg(danielU, "PATIENT_VISIBLE", "The sore throat is much better, thank you. Do I still need the follow-up appointment?"),
                msg(vanceU, "PATIENT_VISIBLE", "Glad to hear it. Yes - please keep the follow-up so we can check your blood pressure and how the new medication is working."));
        thread(walter, "Blood pressure readings", chen,
                msg(chenU, "PATIENT_VISIBLE", "Hello Mr. Nguyen - your log looks good overall. Keep recording and bring it to your next visit."),
                msg(chenU, "INTERNAL", "Nguyen: average 128/82 over 30 days. Consider no change to losartan for now."));
        thread(aisha, "New patient forms", romero,
                msg(maya, "PATIENT_VISIBLE", "Welcome to Oakfield, Aisha! Your intake forms are still pending - please upload them before your first visit."));
        thread(tomas, "School physical", romero,
                msg(romeroU, "PATIENT_VISIBLE", "Hi Tomas - bring your vaccination record and sports clearance form to the visit. See you soon."));
        thread(priyaR, "Diet questions", chen,
                msg(chenU, "PATIENT_VISIBLE", "Mrs. Raman - a dietitian review is available at no extra cost. Would you like me to refer you?"),
                msg(chenU, "INTERNAL", "Raman: consider referral to dietitian after next A1C."));
        thread(sofia, "Sports clearance", nair,
                msg(nairU, "PATIENT_VISIBLE", "Hi Sofia - your sports clearance physical is booked. Remember to wear comfortable clothes for the fitness portion."));
        thread(grace, "Lab results question", vance,
                msg(graceU, "PATIENT_VISIBLE", "Good morning - the portal says my A1C results are in. Can you tell me if anything changed?"),
                msg(vanceU, "PATIENT_VISIBLE", "Your A1C came back at 6.8%, which is right where we want it. Great work keeping your routine steady."),
                msg(vanceU, "INTERNAL", "Lin: A1C stable at 6.8%. Review at next annual."),
                msg(graceU, "PATIENT_VISIBLE", "That's a relief - thank you!"));

        // Read-state: leave some threads unread to make dashboards meaningful
        markUnreadFor(vanceU, "Test results");
        markUnreadFor(vanceU, "Refill timing");
        markUnreadFor(maya, "Question about my prescription");
        markUnreadFor(chenU, "School physical");
    }

    private void thread(Patient patient, String subject, Provider provider, SeedMessage... messages) {
        MessageThread t = new MessageThread();
        t.setPatient(patient);
        t.setSubject(subject);
        t.setCreatedBy(messages.length > 0 ? messages[0].sender().getId() : maya.getId());
        t.setCreatedAt(LocalDateTime.now().minusDays(3));
        t.setLastMessageAt(LocalDateTime.now().minusDays(1));
        em.persist(t);
        // Mark read by default for the patient, so unread states are deliberate
        for (SeedMessage m : messages) {
            Message msg = new Message();
            msg.setThread(t);
            msg.setSenderUserId(m.sender().getId());
            msg.setVisibility(MessageVisibility.valueOf(m.visibility()));
            msg.setBody(m.body());
            msg.setCreatedAt(LocalDateTime.now().minusDays(2));
            em.persist(msg);
            audit.record(ctxFor(m.sender()), m.visibility().equals("INTERNAL") ? AuditService.NOTE_ADDED : AuditService.MESSAGE_SENT,
                    "MESSAGE_THREAD", String.valueOf(t.getId()), null, Json.obj("subject", subject));
        }
        if (patient.getUser() != null) {
            ThreadReadState rs = new ThreadReadState();
            rs.setThread(t);
            rs.setUserId(patient.getUser().getId());
            rs.setLastReadAt(LocalDateTime.now().minusHours(1));
            em.persist(rs);
        }
    }

    private record SeedMessage(User sender, String visibility, String body) { }

    private static SeedMessage msg(User sender, String visibility, String body) {
        return new SeedMessage(sender, visibility, body);
    }

    private void markUnreadFor(User user, String subject) {
        MessageThread t = em.createQuery(
                        "select t from MessageThread t where t.subject = :s", MessageThread.class)
                .setParameter("s", subject).getResultStream().findFirst().orElse(null);
        if (t == null) return;
        ThreadReadState rs = em.createQuery(
                        "select r from ThreadReadState r where r.thread.id = :tid and r.userId = :uid", ThreadReadState.class)
                .setParameter("tid", t.getId()).setParameter("uid", user.getId())
                .getResultStream().findFirst().orElse(null);
        if (rs != null) {
            rs.setLastReadAt(LocalDateTime.now().minusDays(30)); // everything newer counts as unread
        }
    }

    // ------------------------------------------------------------ notifications, audit, webhook

    private void seedNotificationsAndAudit() {
        notifications.notify(danielU.getId(), NotificationType.FORM, "Form received",
                "The clinic received your intake form. We'll review it before your next visit.",
                "/app/patient/documents.xhtml");
        notifications.notify(danielU.getId(), NotificationType.PRESCRIPTION, "Refill approved",
                "Your albuterol refill was approved and sent to the pharmacy.", "/app/patient/prescriptions.xhtml");
        notifications.notify(graceU.getId(), NotificationType.PRESCRIPTION, "More information needed",
                "Dr. Vance needs more information before refilling Metformin.", "/app/patient/prescriptions.xhtml");
        notifications.notify(maya.getId(), NotificationType.FORM, "Forms awaiting verification",
                "Several patient forms are waiting for review.", "/app/staff/frontdesk/forms.xhtml");
        notifications.notify(vanceU.getId(), NotificationType.PRESCRIPTION, "Refill requests waiting",
                "You have refill requests to review.", "/app/staff/clinician/refills.xhtml");

        // A few mocked outbound messages so the admin outbox viewer has content.
        outbox.enqueueEmail(danielU.getEmail(), "Welcome to " + settings.clinicName(),
                "Hi " + danielU.getFullName() + ",\n\nYour patient portal account is ready. Sign in to book "
                        + "appointments, message the clinic, and view your records.\n\n" + settings.baseUrl() + "/login.xhtml");
        outbox.enqueueEmail(graceU.getEmail(), "Your form was verified",
                "Hi " + graceU.getFullName() + ",\n\nYour insurance card document was reviewed and accepted.");
        outbox.enqueueSms(daniel.getPhone(), settings.clinicName() + " reminder: your next appointment is coming up. "
                + "Log in to the portal to view the details.");

        // A few representative read/download audit events (chart reads are one event per request)
        audit.record(vanceCtx(), AuditService.CHART_VIEW, "PATIENT", String.valueOf(daniel.getId()),
                null, Json.obj("records", "42", "tab", "overview"), "role=CLINICIAN;scopes=patient.read;session=seed");
        audit.record(chenCtx(), AuditService.CHART_VIEW, "PATIENT", String.valueOf(omar.getId()),
                null, Json.obj("records", "31", "tab", "overview"), "role=CLINICIAN;scopes=patient.read;session=seed");
        audit.record(mayaCtx(), AuditService.DOC_DOWNLOADED, "DOCUMENT", "1",
                null, Json.obj("patient", String.valueOf(daniel.getId())), null);
        for (User u : List.of(admin, vanceU, maya, danielU)) {
            audit.record(ctxFor(u), AuditService.LOGIN_SUCCESS, "USER", String.valueOf(u.getId()),
                    null, Json.obj("email", u.getEmail()));
        }
    }

    private void seedWebhook() {
        try {
            integrations.createSubscription(adminCtx(),
                    settings.baseUrl() + "/api/v1/webhooks/echo",
                    "seed-webhook-secret-123",
                    List.of("REFILL_APPROVED", "APPOINTMENT_CANCELLED"));
            // Fire one demo event so the deliveries/echo views show a live example.
            integrations.dispatch("REFILL_APPROVED",
                    Json.obj("prescriptionId", "1", "patientId", "1", "medication", "Albuterol inhaler",
                            "reference", "PHA-DEMO0001", "via", "seed"));
        } catch (Exception e) {
            LOG.warnf("Could not seed webhook subscription: %s", e.getMessage());
        }
    }

    // ------------------------------------------------------------ contexts and dates

    private DecisionContext ctxFor(User u) {
        return new DecisionContext(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(),
                decision.scopesForRole(u.getRole()), decision.limitsForRole(u.getRole()),
                0, null, "seed", "seed");
    }

    private DecisionContext adminCtx() { return ctxFor(admin); }
    private DecisionContext mayaCtx() { return ctxFor(maya); }
    private DecisionContext vanceCtx() { return ctxFor(vanceU); }
    private DecisionContext chenCtx() { return ctxFor(chenU); }
    private DecisionContext danielCtx() { return ctxFor(danielU); }
    private DecisionContext graceCtx() { return ctxFor(graceU); }
    private DecisionContext omarCtx() { return ctxFor(omarU); }

    private DecisionContext providerCtx(Provider p) {
        return ctxFor(p.getUser());
    }

    private static LocalDate today() { return LocalDate.now(); }

    private static LocalDate daysAgo(int n) { return LocalDate.now().minusDays(n); }

    /** Next date at least n days ahead that is not a weekend (seeds stay valid any day). */
    private static LocalDate nextWorkday(int n) {
        LocalDate d = LocalDate.now().plusDays(n);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private static LocalDateTime at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute);
    }
}
