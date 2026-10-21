package com.oakfield.clinic.svc.jobs;

import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.NotificationType;
import com.oakfield.clinic.svc.NotificationService;
import com.oakfield.clinic.svc.OutboxService;
import com.oakfield.clinic.svc.SettingsService;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** In-process scheduled workflow: next-day appointment reminders (SMS + in-app). */
@Singleton
@Startup
public class ReminderJob {

    private static final Logger LOG = Logger.getLogger(ReminderJob.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE MMM d 'at' h:mm a");

    @PersistenceContext
    private EntityManager em;

    @EJB
    private OutboxService outbox;

    @EJB
    private NotificationService notifications;

    @EJB
    private SettingsService settings;

    @Schedule(hour = "7", minute = "0", persistent = false)
    public void run() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Appointment> due = em.createQuery(
                        "select a from Appointment a where a.status in :live and a.startTime >= :from and a.startTime < :to",
                        Appointment.class)
                .setParameter("live", List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.REQUESTED))
                .setParameter("from", tomorrow.atStartOfDay())
                .setParameter("to", tomorrow.plusDays(1).atStartOfDay())
                .getResultList();
        int sent = 0;
        for (Appointment a : due) {
            if (a.getPatient().getPhone() == null && a.getPatient().getUser() == null) continue;
            String text = settings.clinicName() + " reminder: you have an appointment with "
                    + a.getProvider().fullName() + " on " + a.getStartTime().format(FMT) + ".";
            if (a.getPatient().getPhone() != null) {
                outbox.enqueueSms(a.getPatient().getPhone(), text);
            }
            if (a.getPatient().getUser() != null) {
                notifications.notify(a.getPatient().getUser().getId(), NotificationType.APPOINTMENT,
                        "Appointment reminder", text, "/app/patient/appointments.xhtml");
            }
            sent++;
        }
        if (sent > 0) LOG.infof("Sent %d appointment reminders", sent);
    }
}
