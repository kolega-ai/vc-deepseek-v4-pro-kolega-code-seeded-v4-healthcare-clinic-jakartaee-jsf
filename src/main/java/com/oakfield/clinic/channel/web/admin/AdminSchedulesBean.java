package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.Provider;
import com.oakfield.clinic.model.ProviderBlockedDate;
import com.oakfield.clinic.model.ProviderSchedule;
import com.oakfield.clinic.svc.ProviderService;
import com.oakfield.clinic.svc.ScheduleService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Clinic-admin provider schedules: weekly work hours and blocked dates. */
@Named("adminSchedules")
@ViewScoped
public class AdminSchedulesBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private ScheduleService schedules;
    @Inject
    private ProviderService providers;

    private List<Provider> providerList;
    private Map<Long, List<ProviderSchedule>> scheduleMap;
    private Map<Long, List<ProviderBlockedDate>> blockedMap;

    // add schedule form
    private Long providerId;
    private int dayOfWeek = 1;
    private String startTime = "09:00";
    private String endTime = "17:00";

    // blocked date form
    private Long blockedProviderId;
    private LocalDate blockedDate;
    private String blockedReason;

    @PostConstruct
    public void load() {
        providerList = providers.listAll();
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(60);
        scheduleMap = new HashMap<>();
        blockedMap = new HashMap<>();
        for (Provider p : providerList) {
            scheduleMap.put(p.getId(), schedules.schedulesFor(p.getId()));
            blockedMap.put(p.getId(), schedules.blockedDatesFor(p.getId(), from, to));
        }
    }

    public void addSchedule() {
        try {
            if (providerId == null) throw new ValidationException("Pick a provider.");
            schedules.saveSchedule(session.ctx(), providerId, dayOfWeek,
                    parseTime(startTime), parseTime(endTime));
            FacesUtil.info("Schedule saved", dayName(dayOfWeek) + " " + startTime + "-" + endTime + " is on the books.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not save schedule", e.getMessage());
        }
    }

    public void removeSchedule(long id) {
        try {
            schedules.removeSchedule(session.ctx(), id);
            FacesUtil.info("Schedule removed", "The weekly slot was removed.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not remove schedule", e.getMessage());
        }
    }

    public void addBlockedDate() {
        try {
            if (blockedProviderId == null) throw new ValidationException("Pick a provider.");
            schedules.addBlockedDate(session.ctx(), blockedProviderId, blockedDate, blockedReason);
            FacesUtil.info("Date blocked", "The provider is unavailable that day.");
            blockedDate = null;
            blockedReason = null;
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not block date", e.getMessage());
        }
    }

    public void removeBlockedDate(long id) {
        try {
            schedules.removeBlockedDate(session.ctx(), id);
            FacesUtil.info("Blocked date removed", "The day is open again.");
            load();
        } catch (ValidationException e) {
            FacesUtil.error("Could not remove blocked date", e.getMessage());
        }
    }

    public String dayName(int d) {
        return switch (d) {
            case 1 -> "Monday";
            case 2 -> "Tuesday";
            case 3 -> "Wednesday";
            case 4 -> "Thursday";
            case 5 -> "Friday";
            case 6 -> "Saturday";
            case 7 -> "Sunday";
            default -> "Day " + d;
        };
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) throw new ValidationException("Both times are required.");
        try {
            return LocalTime.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Times must be in HH:mm format (24-hour).");
        }
    }

    public List<Provider> getProviderList() { return providerList; }
    public Map<Long, List<ProviderSchedule>> getScheduleMap() { return scheduleMap; }
    public Map<Long, List<ProviderBlockedDate>> getBlockedMap() { return blockedMap; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public Long getBlockedProviderId() { return blockedProviderId; }
    public void setBlockedProviderId(Long blockedProviderId) { this.blockedProviderId = blockedProviderId; }
    public LocalDate getBlockedDate() { return blockedDate; }
    public void setBlockedDate(LocalDate blockedDate) { this.blockedDate = blockedDate; }
    public String getBlockedReason() { return blockedReason; }
    public void setBlockedReason(String blockedReason) { this.blockedReason = blockedReason; }
}
