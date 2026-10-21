package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.AccessDeniedException;
import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentType;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.model.ProviderBlockedDate;
import com.oakfield.clinic.model.ProviderSchedule;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Stateless
public class ScheduleService {

    public static final int SLOT_STEP_MINUTES = 15;

    @PersistenceContext
    private EntityManager em;

    public List<ProviderSchedule> schedulesFor(Long providerId) {
        return em.createQuery(
                        "select s from ProviderSchedule s where s.provider.id = :pid and s.active = true order by s.dayOfWeek",
                        ProviderSchedule.class)
                .setParameter("pid", providerId)
                .getResultList();
    }

    public List<ProviderBlockedDate> blockedDatesFor(Long providerId, LocalDate from, LocalDate to) {
        return em.createQuery(
                        "select b from ProviderBlockedDate b where b.provider.id = :pid and b.blockDate >= :from and b.blockDate <= :to",
                        ProviderBlockedDate.class)
                .setParameter("pid", providerId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    /** Is the provider scheduled to work on this day at all? */
    public boolean worksOnDay(Provider provider, LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return schedulesFor(provider.getId()).stream()
                .anyMatch(s -> s.getDayOfWeek() == dow.getValue() && effectiveOn(s, date));
    }

    private boolean effectiveOn(ProviderSchedule s, LocalDate date) {
        if (s.getEffectiveFrom() != null && date.isBefore(s.getEffectiveFrom())) return false;
        if (s.getEffectiveTo() != null && date.isAfter(s.getEffectiveTo())) return false;
        return true;
    }

    /**
     * Available start slots for a provider/date/type. Slots come from the
     * provider's weekly schedule minus blocked dates minus overlapping
     * appointments, in 15-minute steps. Never trusts client-supplied
     * schedule data.
     */
    public List<LocalDateTime> availableSlots(Long providerId, LocalDate date, AppointmentType type) {
        List<LocalDateTime> slots = new ArrayList<>();
        Provider provider = em.find(Provider.class, providerId);
        if (provider == null || !provider.isActive()) return slots;
        DayOfWeek dow = date.getDayOfWeek();
        int duration = type.getDurationMinutes();
        List<LocalDateTime> busyStarts = new ArrayList<>();
        for (Appointment a : em.createQuery(
                        "select a from Appointment a where a.provider.id = :pid and a.startTime >= :from and a.startTime < :to " +
                        "and a.status not in :ignored", Appointment.class)
                .setParameter("pid", providerId)
                .setParameter("from", date.atStartOfDay())
                .setParameter("to", date.plusDays(1).atStartOfDay())
                .setParameter("ignored", List.of(com.oakfield.clinic.model.AppointmentStatus.CANCELLED,
                        com.oakfield.clinic.model.AppointmentStatus.NO_SHOW))
                .getResultList()) {
            busyStarts.add(a.getStartTime());
        }
        boolean blocked = !blockedDatesFor(providerId, date, date).isEmpty();

        for (ProviderSchedule s : schedulesFor(providerId)) {
            if (s.getDayOfWeek() != dow.getValue() || !effectiveOn(s, date) || blocked) continue;
            LocalTime cursor = s.getStartTime();
            LocalTime latest = s.getEndTime().minusMinutes(duration);
            while (!cursor.isAfter(latest)) {
                LocalDateTime candidate = date.atTime(cursor);
                boolean clash = busyStarts.stream().anyMatch(b ->
                        !candidate.isBefore(b) && candidate.isBefore(b.plusMinutes(type.getDurationMinutes()))
                                || !b.isBefore(candidate) && b.isBefore(candidate.plusMinutes(type.getDurationMinutes())));
                if (!clash) slots.add(candidate);
                cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
            }
        }
        return slots;
    }

    // ------------------------------------------------------------- admin CRUD

    public void saveSchedule(DecisionContext ctx, Long providerId, int dayOfWeek, LocalTime start, LocalTime end) {
        ctx.require("schedule.manage");
        if (dayOfWeek < 1 || dayOfWeek > 7) throw new ValidationException("Day of week must be 1-7.");
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ValidationException("End time must be after start time.");
        }
        Provider provider = em.find(Provider.class, providerId);
        if (provider == null) throw new NotFoundException("Provider not found.");
        ProviderSchedule s = em.createQuery(
                        "select s from ProviderSchedule s where s.provider.id = :pid and s.dayOfWeek = :dow and s.active = true",
                        ProviderSchedule.class)
                .setParameter("pid", providerId)
                .setParameter("dow", dayOfWeek)
                .getResultStream().findFirst().orElse(null);
        if (s == null) {
            s = new ProviderSchedule();
            s.setProvider(provider);
            s.setDayOfWeek(dayOfWeek);
            s.setActive(true);
        }
        s.setStartTime(start);
        s.setEndTime(end);
        em.persist(s);
        audit.record(ctx, AuditService.SCHEDULE_CHANGED, "PROVIDER_SCHEDULE", String.valueOf(providerId),
                Json.obj("dayOfWeek", String.valueOf(dayOfWeek)),
                Json.obj("dayOfWeek", String.valueOf(dayOfWeek), "start", String.valueOf(start), "end", String.valueOf(end)));
    }

    public void removeSchedule(DecisionContext ctx, long scheduleId) {
        ctx.require("schedule.manage");
        ProviderSchedule s = em.find(ProviderSchedule.class, scheduleId);
        if (s == null) throw new NotFoundException("Schedule not found.");
        em.remove(s);
        audit.record(ctx, AuditService.SCHEDULE_CHANGED, "PROVIDER_SCHEDULE", String.valueOf(s.getProvider().getId()),
                Json.obj("dayOfWeek", String.valueOf(s.getDayOfWeek())), Json.obj("removed", "true"));
    }

    public void addBlockedDate(DecisionContext ctx, long providerId, LocalDate date, String reason) {
        ctx.require("schedule.manage");
        if (date == null || date.isBefore(LocalDate.now())) throw new ValidationException("Block date must be today or later.");
        Provider provider = em.find(Provider.class, providerId);
        if (provider == null) throw new NotFoundException("Provider not found.");
        ProviderBlockedDate b = new ProviderBlockedDate();
        b.setProvider(provider);
        b.setBlockDate(date);
        b.setReason(reason == null || reason.isBlank() ? null : reason.strip());
        em.persist(b);
        audit.record(ctx, AuditService.SCHEDULE_CHANGED, "PROVIDER_BLOCKED_DATE", String.valueOf(providerId),
                null, Json.obj("blockDate", String.valueOf(date)));
    }

    public void removeBlockedDate(DecisionContext ctx, long blockedId) {
        ctx.require("schedule.manage");
        ProviderBlockedDate b = em.find(ProviderBlockedDate.class, blockedId);
        if (b != null) em.remove(b);
    }

    @jakarta.ejb.EJB
    private AuditService audit;

    // package-visible seam for the audit service
    void setAuditServiceForTest(AuditService auditService) { this.audit = auditService; }
}
