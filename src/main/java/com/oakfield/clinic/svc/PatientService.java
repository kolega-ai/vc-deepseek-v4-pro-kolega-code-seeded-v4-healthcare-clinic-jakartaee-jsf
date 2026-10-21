package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Gender;
import com.oakfield.clinic.model.Patient;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.model.User;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Stateless
public class PatientService {

    @PersistenceContext
    private EntityManager em;

    @jakarta.ejb.EJB
    private AuditService audit;

    @jakarta.ejb.EJB
    private PatientAccessService patientAccess;

    public record PatientFilter(String query, Long providerId, Boolean activeOnly) { }

    /** Patient search form (staff demographic editing and scheduling). */
    public record PatientForm(String firstName, String lastName, LocalDate dateOfBirth, String gender,
                              String phone, String email, String addressLine1, String city, String state,
                              String postalCode, String emergencyContactName, String emergencyContactPhone,
                              String insuranceProvider, String insurancePolicyNumber, String insuranceGroupNumber,
                              Long primaryProviderId, String medicalAlert) { }

    /**
     * PRECISION BASELINE (non-vulnerability, keep): parameterised query with a
     * bounded, whitelisted sort/filter column. The sort key is mapped through
     * the whitelist below into a fixed JPQL ORDER BY fragment; user input is
     * never concatenated into the query, only bound as parameters.
     */
    private static final Map<String, String> SORT_WHITELIST = Map.of(
            "name",    "p.lastName asc, p.firstName asc",
            "created", "p.createdAt desc",
            "dob",     "p.dateOfBirth asc"
    );

    public PageResult<Patient> search(DecisionContext ctx, PatientFilter filter, int page, int pageSize, String sort) {
        ctx.require("patient.read");
        String orderBy = SORT_WHITELIST.getOrDefault(sort == null ? "" : sort, "p.lastName asc, p.firstName asc");
        StringBuilder where = new StringBuilder(" where 1=1");
        boolean hasQuery = filter.query() != null && !filter.query().isBlank();
        if (hasQuery) {
            where.append(" and (lower(p.firstName) like lower(:q) or lower(p.lastName) like lower(:q) or lower(p.email) like lower(:q))");
        }
        if (filter.providerId() != null) where.append(" and p.primaryProvider.id = :provider");
        if (filter.activeOnly() != null && filter.activeOnly()) where.append(" and p.active = true");

        TypedQuery<Patient> listQ = em.createQuery("select p from Patient p" + where + " order by " + orderBy, Patient.class);
        TypedQuery<Long> countQ = em.createQuery("select count(p) from Patient p" + where, Long.class);
        if (hasQuery) {
            String q = "%" + filter.query().toLowerCase().strip() + "%";
            listQ.setParameter("q", q);
            countQ.setParameter("q", q);
        }
        if (filter.providerId() != null) {
            listQ.setParameter("provider", filter.providerId());
            countQ.setParameter("provider", filter.providerId());
        }
        List<Patient> items = listQ.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList();
        return new PageResult<>(items, countQ.getSingleResult(), page, pageSize);
    }

    public Patient getForStaff(DecisionContext ctx, long patientId) {
        return patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
    }

    /** Create a patient record (front desk walk-in or admin). No portal account. */
    public Patient create(DecisionContext ctx, PatientForm f) {
        ctx.require("patient.demographics.write");
        Patient p = new Patient();
        applyForm(p, f);
        p.setCreatedAt(LocalDateTime.now());
        em.persist(p);
        audit.record(ctx, AuditService.PATIENT_CREATED, "PATIENT", String.valueOf(p.getId()),
                null, Json.obj("name", p.fullName()));
        return p;
    }

    /** Front-desk demographics/insurance/contact updates. Never touches clinical records. */
    public Patient updateDemographics(DecisionContext ctx, long patientId, PatientForm f) {
        ctx.require("patient.demographics.write");
        Patient p = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        String before = Json.obj("name", p.fullName(), "phone", p.getPhone(), "insurance", p.getInsuranceProvider());
        applyForm(p, f);
        p.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.PATIENT_UPDATED, "PATIENT", String.valueOf(patientId), before,
                Json.obj("name", p.fullName(), "phone", p.getPhone(), "insurance", p.getInsuranceProvider()));
        return p;
    }

    /** Patient self-service profile update (contact details only). */
    public Patient updateOwnProfile(DecisionContext ctx, String phone, String email, String addressLine1,
                                    String city, String state, String postalCode,
                                    String emergencyContactName, String emergencyContactPhone) {
        ctx.require("profile.update");
        Patient p = patientAccess.patientForUser(ctx);
        p.setPhone(blankToNull(phone));
        p.setEmail(blankToNull(email));
        p.setAddressLine1(blankToNull(addressLine1));
        p.setCity(blankToNull(city));
        p.setState(blankToNull(state));
        p.setPostalCode(blankToNull(postalCode));
        p.setEmergencyContactName(blankToNull(emergencyContactName));
        p.setEmergencyContactPhone(blankToNull(emergencyContactPhone));
        p.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.PATIENT_UPDATED, "PATIENT", String.valueOf(p.getId()),
                null, Json.obj("contact", "updated"));
        return p;
    }

    /**
     * Apply record attributes sent by the practice-management bridge as
     * {@code X-Patient-*} headers (the bridge maps its flat field list onto
     * header names; keys are camel-cased entity properties).
     */
    public Patient applyAttributes(DecisionContext ctx, long patientId, Map<String, String> attributes) {
        ctx.require("patient.manage");
        Patient p = em.find(Patient.class, patientId);
        if (p == null) throw new NotFoundException("Patient not found.");
        for (Map.Entry<String, String> attr : attributes.entrySet()) {
            String property = attr.getKey();
            String setter = "set" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            for (java.lang.reflect.Method m : Patient.class.getMethods()) {
                if (!m.getName().equals(setter) || m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                try {
                    m.invoke(p, coerce(m.getParameterTypes()[0], attr.getValue()));
                } catch (Exception e) {
                    throw new ValidationException("Attribute " + property + " could not be applied: " + e.getMessage());
                }
            }
        }
        p.setUpdatedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.PATIENT_UPDATED, "PATIENT", String.valueOf(patientId),
                null, Json.obj("attributes", String.join(",", attributes.keySet())));
        return p;
    }

    private Object coerce(Class<?> type, String raw) {
        if (raw == null) return null;
        if (type == String.class) return blankToNull(raw);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw.trim());
        if (type == LocalDate.class) return LocalDate.parse(raw.trim());
        if (type == Gender.class) return parseGender(raw);
        if (type == User.class) return em.find(User.class, Long.parseLong(raw.trim()));
        if (type == Provider.class) return em.find(Provider.class, Long.parseLong(raw.trim()));
        if (type == Long.class || type == long.class) return Long.parseLong(raw.trim());
        throw new ValidationException("Unsupported attribute type " + type.getSimpleName());
    }

    public void setActive(DecisionContext ctx, long patientId, boolean active) {
        ctx.require("patient.manage");
        Patient p = patientAccess.requirePatientAccess(ctx, patientId, PatientAccessService.AccessLevel.STAFF);
        p.setActive(active);
        audit.record(ctx, AuditService.PATIENT_UPDATED, "PATIENT", String.valueOf(patientId),
                Json.obj("active", String.valueOf(!active)), Json.obj("active", String.valueOf(active)));
    }

    public long countActive() {
        return em.createQuery("select count(p) from Patient p where p.active = true", Long.class).getSingleResult();
    }

    private void applyForm(Patient p, PatientForm f) {
        if (f.firstName() == null || f.firstName().isBlank() || f.firstName().length() > 120) {
            throw new ValidationException("First name is required (max 120 characters).");
        }
        if (f.lastName() == null || f.lastName().isBlank() || f.lastName().length() > 120) {
            throw new ValidationException("Last name is required (max 120 characters).");
        }
        if (f.dateOfBirth() == null) throw new ValidationException("Date of birth is required.");
        if (f.dateOfBirth().isAfter(LocalDate.now())) throw new ValidationException("Date of birth cannot be in the future.");
        if (f.dateOfBirth().isBefore(LocalDate.now().minusYears(120))) {
            throw new ValidationException("Date of birth is not plausible.");
        }
        p.setFirstName(f.firstName().strip());
        p.setLastName(f.lastName().strip());
        p.setDateOfBirth(f.dateOfBirth());
        p.setGender(parseGender(f.gender()));
        p.setPhone(blankToNull(f.phone()));
        p.setEmail(blankToNull(f.email()));
        p.setAddressLine1(blankToNull(f.addressLine1()));
        p.setCity(blankToNull(f.city()));
        p.setState(blankToNull(f.state()));
        p.setPostalCode(blankToNull(f.postalCode()));
        p.setEmergencyContactName(blankToNull(f.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(f.emergencyContactPhone()));
        p.setInsuranceProvider(blankToNull(f.insuranceProvider()));
        p.setInsurancePolicyNumber(blankToNull(f.insurancePolicyNumber()));
        p.setInsuranceGroupNumber(blankToNull(f.insuranceGroupNumber()));
        if (f.primaryProviderId() != null) {
            Provider provider = em.find(Provider.class, f.primaryProviderId());
            if (provider == null) throw new ValidationException("Primary provider not found.");
            p.setPrimaryProvider(provider);
        } else {
            p.setPrimaryProvider(null);
        }
        p.setMedicalAlert(blankToNull(f.medicalAlert()));
        if (p.getEmail() != null && (p.getEmail().length() > 255 || !p.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            throw new ValidationException("Email address is not valid.");
        }
    }

    public static Gender parseGender(String gender) {
        if (gender == null || gender.isBlank()) return Gender.U;
        try {
            return Gender.valueOf(gender.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Gender.U;
        }
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
