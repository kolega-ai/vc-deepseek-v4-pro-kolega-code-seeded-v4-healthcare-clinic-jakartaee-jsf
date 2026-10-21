package com.oakfield.clinic.svc.jobs;

import com.oakfield.clinic.svc.OutboxService;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.jboss.logging.Logger;

/** In-process scheduled job: processes the local email/SMS mock outbox. */
@Singleton
@Startup
public class OutboxProcessorJob {

    private static final Logger LOG = Logger.getLogger(OutboxProcessorJob.class);

    @EJB
    private OutboxService outbox;

    @Schedule(hour = "*", minute = "*", second = "*/30", persistent = false)
    public void run() {
        try {
            outbox.processPendingBatch(50);
        } catch (Exception e) {
            LOG.warnf("Outbox processor run failed: %s", e.getMessage());
        }
    }
}
