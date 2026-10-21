package com.oakfield.clinic.svc.jobs;

import com.oakfield.clinic.decision.DecisionService;
import jakarta.ejb.EJB;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.jboss.logging.Logger;

/** In-process scheduled job: revokes expired decision sessions. */
@Singleton
@Startup
public class SessionJanitorJob {

    private static final Logger LOG = Logger.getLogger(SessionJanitorJob.class);

    @EJB
    private DecisionService decision;

    @Schedule(hour = "*", minute = "*/5", persistent = false)
    public void run() {
        try {
            decision.revokeExpiredSessions();
        } catch (Exception e) {
            LOG.warnf("Session janitor failed: %s", e.getMessage());
        }
    }
}
