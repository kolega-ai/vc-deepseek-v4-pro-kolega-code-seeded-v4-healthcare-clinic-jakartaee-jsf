package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.ClinicSetting;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Stateless
public class SettingsService {

    private static final Logger LOG = Logger.getLogger(SettingsService.class);

    @PersistenceContext
    private EntityManager em;

    @jakarta.ejb.EJB
    private AuditService audit;

    /** Admin update of a clinic setting (audited). Only known keys may be edited. */
    public void set(com.oakfield.clinic.decision.DecisionContext ctx, String key, String value) {
        ctx.require("setting.manage");
        if (key == null || !key.matches("[a-z0-9._-]{2,80}")) {
            throw new ValidationException("Setting key is invalid.");
        }
        ClinicSetting s = em.createQuery("select s from ClinicSetting s where s.key = :k", ClinicSetting.class)
                .setParameter("k", key)
                .getResultStream().findFirst().orElse(null);
        if (s == null) throw new ValidationException("Unknown setting: " + key);
        String before = s.getValue();
        s.setValue(value == null ? "" : value.strip());
        audit.record(ctx, AuditService.SETTING_CHANGED, "CLINIC_SETTING", key,
                Json.obj("value", before), Json.obj("value", s.getValue()));
    }

    public String get(String key, String defaultValue) {
        ClinicSetting s = em.createQuery("select s from ClinicSetting s where s.key = :k", ClinicSetting.class)
                .setParameter("k", key)
                .getResultStream().findFirst().orElse(null);
        return s == null ? defaultValue : s.getValue();
    }

    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v.trim());
    }

    /** Fixed-precision money values from settings (insurance-adjacent display data). */
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try { return new BigDecimal(get(key, defaultValue.toPlainString()).trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    /**
     * Apply an operations configuration pack (YAML) exported from another clinic
     * or the staging environment. The pack lists setting keys and their values.
     */
    public int applyConfigurationPack(DecisionContext ctx, java.io.InputStream pack) {
        ctx.require("setting.manage");
        if (pack == null) throw new ValidationException("Choose a configuration pack.");
        Object loaded = new org.yaml.snakeyaml.Yaml()
                .load(new java.io.InputStreamReader(pack, java.nio.charset.StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new ValidationException("The configuration pack is not a key/value document.");
        }
        int applied = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            set(ctx, String.valueOf(entry.getKey()), value);
            applied++;
        }
        return applied;
    }

    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        em.createQuery("select s from ClinicSetting s order by s.key", ClinicSetting.class)
                .getResultList().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    public String clinicName() { return get("clinic.name", "Oakfield Family Clinic"); }
    public String clinicPhone() { return get("clinic.phone", ""); }
    public String clinicEmail() { return get("clinic.email", ""); }
    public String clinicAddress() { return get("clinic.address", ""); }
    public String clinicHours() { return get("clinic.hours", ""); }
    public boolean demoShowEmailLinks() { return getBool("demo.show_email_links", false); }
    public String baseUrl() {
        String v = System.getenv("CLINIC_BASE_URL");
        return v == null || v.isBlank() ? "http://localhost:8080/clinic-portal" : v;
    }
}
