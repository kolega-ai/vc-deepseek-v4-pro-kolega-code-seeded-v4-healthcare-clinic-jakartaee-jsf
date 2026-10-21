package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.AppointmentType;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.List;

@Stateless
public class AppointmentTypeService {

    @PersistenceContext
    private EntityManager em;

    @jakarta.ejb.EJB
    private AuditService audit;

    public List<AppointmentType> listActive() {
        return em.createQuery(
                        "select t from AppointmentType t where t.active = true order by t.sortOrder, t.name", AppointmentType.class)
                .getResultList();
    }

    public List<AppointmentType> listAll() {
        return em.createQuery("select t from AppointmentType t order by t.sortOrder, t.name", AppointmentType.class)
                .getResultList();
    }

    public AppointmentType get(long id) {
        AppointmentType t = em.find(AppointmentType.class, id);
        if (t == null) throw new NotFoundException("Appointment type not found.");
        return t;
    }

    public void create(DecisionContext ctx, String code, String name, String description,
                       int durationMinutes, BigDecimal feeAmount, String feeCurrency, boolean active) {
        ctx.require("schedule.manage");
        validate(code, name, durationMinutes, feeAmount, feeCurrency);
        AppointmentType t = new AppointmentType();
        t.setCode(code.strip().toUpperCase());
        t.setName(name.strip());
        t.setDescription(blankToNull(description));
        t.setDurationMinutes(durationMinutes);
        t.setFeeAmount(feeAmount);
        t.setFeeCurrency(feeCurrency.strip().toUpperCase());
        t.setActive(active);
        t.setSortOrder(99);
        em.persist(t);
        audit.record(ctx, AuditService.APPOINTMENT_TYPE_CHANGED, "APPOINTMENT_TYPE", null, null,
                Json.obj("id", String.valueOf(t.getId()), "code", t.getCode(), "fee", feeAmount.toPlainString(), "currency", t.getFeeCurrency()));
    }

    public void update(DecisionContext ctx, long id, String name, String description,
                       int durationMinutes, BigDecimal feeAmount, String feeCurrency, boolean active) {
        ctx.require("schedule.manage");
        AppointmentType t = get(id);
        validate(t.getCode(), name, durationMinutes, feeAmount, feeCurrency);
        t.setName(name.strip());
        t.setDescription(blankToNull(description));
        t.setDurationMinutes(durationMinutes);
        t.setFeeAmount(feeAmount);
        t.setFeeCurrency(feeCurrency.strip().toUpperCase());
        t.setActive(active);
        audit.record(ctx, AuditService.APPOINTMENT_TYPE_CHANGED, "APPOINTMENT_TYPE", String.valueOf(id),
                null, Json.obj("code", t.getCode(), "fee", feeAmount.toPlainString(), "currency", t.getFeeCurrency(), "active", String.valueOf(active)));
    }

    private void validate(String code, String name, int durationMinutes, BigDecimal feeAmount, String feeCurrency) {
        if (code == null || !code.matches("[A-Za-z0-9_]{2,20}")) {
            throw new ValidationException("Code must be 2-20 letters, digits, or underscores.");
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ValidationException("Name is required (max 120 characters).");
        }
        if (durationMinutes < 5 || durationMinutes > 240) {
            throw new ValidationException("Duration must be between 5 and 240 minutes.");
        }
        // Fixed-precision money: scale and non-negativity enforced on the server.
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) < 0 || feeAmount.scale() > 2) {
            throw new ValidationException("Fee must be a non-negative amount with at most 2 decimal places.");
        }
        if (feeCurrency == null || !feeCurrency.matches("[A-Za-z]{3}")) {
            throw new ValidationException("Currency must be a 3-letter code.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
