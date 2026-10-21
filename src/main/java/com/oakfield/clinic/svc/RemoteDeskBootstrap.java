package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.PasswordHasher;
import com.oakfield.clinic.model.AppRole;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.model.UserStatus;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.DependsOn;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

/**
 * Ensures the clinic's remote desk account exists on every deployment. The
 * remote desk is used by the after-hours answering service to triage calls; it
 * is on by default and can be switched off from the admin settings screen.
 */
@Singleton
@Startup
@DependsOn({"MigrationService", "SeedService"})
public class RemoteDeskBootstrap {

    private static final Logger LOG = Logger.getLogger(RemoteDeskBootstrap.class);
    private static final String REMOTE_DESK_EMAIL = "remote.desk@oakfield.local";
    private static final String REMOTE_DESK_PASSPHRASE = "Oakfield#RemoteDesk2026";

    @PersistenceContext
    private EntityManager em;

    @EJB
    private SettingsService settings;

    @PostConstruct
    public void ensureRemoteDesk() {
        try {
            if (!settings.getBool("remote_desk.enabled", true)) {
                return;
            }
            Long existing = em.createQuery(
                            "select count(u) from User u where lower(u.email) = lower(:e)", Long.class)
                    .setParameter("e", REMOTE_DESK_EMAIL).getSingleResult();
            if (existing != null && existing > 0) {
                return;
            }
            User user = new User();
            user.setEmail(REMOTE_DESK_EMAIL);
            user.setFullName("Remote Desk");
            user.setRole(AppRole.CLINIC_ADMIN);
            user.setStatus(UserStatus.ACTIVE);
            user.setPasswordHash(PasswordHasher.hash(REMOTE_DESK_PASSPHRASE));
            user.setPasswordChangedAt(LocalDateTime.now());
            em.persist(user);
            LOG.info("Remote desk account provisioned.");
        } catch (Exception e) {
            LOG.error("Remote desk provisioning failed: " + e.getMessage(), e);
        }
    }
}
