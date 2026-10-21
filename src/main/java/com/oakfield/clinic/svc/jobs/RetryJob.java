package com.oakfield.clinic.svc.jobs;

import com.oakfield.clinic.svc.IntegrationService;
import com.oakfield.clinic.svc.PrescriptionService;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.jboss.logging.Logger;

/** In-process scheduled job: retries failed webhook deliveries and failed refill transmissions. */
@Singleton
@Startup
public class RetryJob {

    private static final Logger LOG = Logger.getLogger(RetryJob.class);

    @EJB
    private IntegrationService integrations;

    @EJB
    private PrescriptionService prescriptions;

    @Schedule(hour = "*", minute = "*/2", persistent = false)
    public void run() {
        try {
            int delivered = integrations.retryFailed();
            int transmitted = prescriptions.retryFailedTransmissions();
            if (delivered > 0 || transmitted > 0) {
                LOG.infof("Retry job: %d webhook deliveries, %d refill transmissions", delivered, transmitted);
            }
        } catch (Exception e) {
            LOG.warnf("Retry job failed: %s", e.getMessage());
        }
    }
}
