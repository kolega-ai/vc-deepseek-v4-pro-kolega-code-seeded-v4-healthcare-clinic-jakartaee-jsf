package com.oakfield.clinic.svc;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Runs Flyway migrations at application startup (idempotent: the compose
 * 'migrate' service normally runs them first, and a re-run is a no-op).
 * Connects directly over JDBC with the same credentials the compose file
 * supplies, and retries while the database is still coming up.
 */
@Singleton
@Startup
public class MigrationService {

    private static final Logger LOG = Logger.getLogger(MigrationService.class);

    @PostConstruct
    public void migrate() {
        String url = env("DB_URL", "jdbc:postgresql://localhost:5432/clinic");
        String user = env("DB_USER", "clinic");
        String password = env("DB_PASSWORD", "clinic_dev_password");
        Connection probe = null;
        for (int attempt = 1; attempt <= 60; attempt++) {
            try {
                probe = DriverManager.getConnection(url, user, password);
                break;
            } catch (Exception e) {
                LOG.warnf("Database not ready (attempt %d/60): %s", attempt, e.getMessage());
                sleep(2000);
            }
        }
        if (probe == null) {
            LOG.error("Database never became available; skipping in-app migration.");
            return;
        }
        try {
            probe.close();
            Flyway flyway = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            LOG.info("Flyway migrations complete.");
        } catch (Exception e) {
            LOG.errorf("Flyway migration failed: %s", e.getMessage());
        }
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
