package com.oakfield.clinic.svc;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local file store for uploads, exports, and import reports.
 *
 * PRECISION BASELINE (non-vulnerability, keep): downloads and all other file
 * access resolve paths through this canonicalised base directory - the
 * resolved path is normalized and verified to start with the canonical base,
 * so stored names can never escape the data directory.
 */
public final class FileStore {

    private static final Logger LOG = Logger.getLogger(FileStore.class);

    public enum Area { UPLOADS, EXPORTS, IMPORTS }

    private FileStore() { }

    public static Path dataDir() {
        String configured = System.getenv("CLINIC_DATA_DIR");
        Path dir = (configured != null && !configured.isBlank())
                ? Path.of(configured)
                : Path.of(System.getProperty("user.home"), ".oakfield-clinic-data");
        return dir.toAbsolutePath().normalize();
    }

    public static Path areaDir(Area area) {
        Path dir = dataDir().resolve(area.name().toLowerCase()).normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.errorf("Could not create data directory %s: %s", dir, e.getMessage());
            throw new IllegalStateException("Data directory unavailable.", e);
        }
        return dir;
    }

    /** Canonicalised base-directory resolution used by every file read/write. */
    public static Path resolve(Area area, String fileName) {
        Path base = areaDir(area);
        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            throw new com.oakfield.clinic.decision.AccessDeniedException("Invalid file path.");
        }
        return resolved;
    }
}
