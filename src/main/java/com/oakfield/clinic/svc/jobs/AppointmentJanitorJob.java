package com.oakfield.clinic.svc.jobs;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Appointment;
import com.oakfield.clinic.model.AppointmentStatus;
import com.oakfield.clinic.model.NotificationType;
import com.oakfield.clinic.svc.AppointmentService;
import com.oakfield.clinic.svc.NotificationService;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

/**
 * In-process scheduled workflow: booking requests that were never confirmed by
 * the front desk are auto-cancelled after 48 hours, through the same state
 * machine and audit path as a manual cancellation (decision context: SYSTEM).
 */
@Singleton
@Startup
public class AppointmentJanitorJob {

    private static final Logger LOG = Logger.getLogger(AppointmentJanitorJob.class);

    @PersistenceContext
    private EntityManager em;

    @EJB
    private AppointmentService appointments;

    @EJB
    private NotificationService notifications;

    @Schedule(hour = "*", minute = "*/30", persistent = false)
    public void run() {
        List<Appointment> stale = em.createQuery(
                        "select a from Appointment a where a.status = :s and a.createdAt < :cutoff",
                        Appointment.class)
                .setParameter("s", AppointmentStatus.REQUESTED)
                .setParameter("cutoff", LocalDateTime.now().minusHours(48))
                .getResultList();
        for (Appointment a : stale) {
            try {
                appointments.cancel(DecisionContext.system("appointment-janitor"), a.getId(),
                        "Not confirmed within 48 hours.");
                if (a.getPatient().getUser() != null) {
                    notifications.notify(a.getPatient().getUser().getId(), NotificationType.APPOINTMENT,
                            "Booking request expired",
                            "Your appointment request for " + a.getStartTime().toLocalDate()
                                    + " was not confirmed and has been cancelled. You can book again anytime.",
                            "/app/patient/appointments.xhtml");
                }
                LOG.infof("Auto-cancelled unconfirmed appointment %d", a.getId());
            } catch (Exception e) {
                LOG.warnf("Could not auto-cancel appointment %d: %s", a.getId(), e.getMessage());
            }
        }
    }
}
