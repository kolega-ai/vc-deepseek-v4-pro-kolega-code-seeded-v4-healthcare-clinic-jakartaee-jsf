package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.model.Provider;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Stateless
public class ProviderService {

    @PersistenceContext
    private EntityManager em;

    @jakarta.ejb.EJB
    private AuditService audit;

    public List<Provider> listActive() {
        return em.createQuery(
                        "select p from Provider p where p.active = true order by p.lastName, p.firstName", Provider.class)
                .getResultList();
    }

    public List<Provider> listAll() {
        return em.createQuery("select p from Provider p order by p.lastName, p.firstName", Provider.class).getResultList();
    }

    public Provider get(long id) {
        Provider p = em.find(Provider.class, id);
        if (p == null) throw new NotFoundException("Provider not found.");
        return p;
    }

    public Provider byNpi(String npi) {
        return em.createQuery("select p from Provider p where p.npi = :npi", Provider.class)
                .setParameter("npi", npi)
                .getResultStream().findFirst().orElse(null);
    }

    public Provider byUserId(long userId) {
        return em.createQuery("select p from Provider p where p.user.id = :uid", Provider.class)
                .setParameter("uid", userId)
                .getResultStream().findFirst().orElse(null);
    }

    public void create(DecisionContext ctx, String firstName, String lastName, String title, String specialty,
                       String npi, String phone, String email) {
        ctx.require("schedule.manage");
        validate(firstName, lastName, npi);
        if (byNpi(npi) != null) throw new ValidationException("NPI already registered.");
        Provider p = new Provider();
        p.setFirstName(firstName.strip());
        p.setLastName(lastName.strip());
        p.setTitle(blankToNull(title));
        p.setSpecialty(blankToNull(specialty));
        p.setNpi(npi.strip());
        p.setPhone(blankToNull(phone));
        p.setEmail(blankToNull(email));
        p.setActive(true);
        em.persist(p);
        audit.record(ctx, AuditService.PROVIDER_CHANGED, "PROVIDER", null, null,
                Json.obj("id", String.valueOf(p.getId()), "npi", p.getNpi()));
    }

    public void update(DecisionContext ctx, long id, String firstName, String lastName, String title, String specialty,
                       String phone, String email, boolean active) {
        ctx.require("schedule.manage");
        Provider p = get(id);
        validate(firstName, lastName, p.getNpi());
        p.setFirstName(firstName.strip());
        p.setLastName(lastName.strip());
        p.setTitle(blankToNull(title));
        p.setSpecialty(blankToNull(specialty));
        p.setPhone(blankToNull(phone));
        p.setEmail(blankToNull(email));
        p.setActive(active);
        audit.record(ctx, AuditService.PROVIDER_CHANGED, "PROVIDER", String.valueOf(id),
                Json.obj("active", String.valueOf(p.isActive())),
                Json.obj("active", String.valueOf(active), "npi", p.getNpi()));
    }

    private void validate(String firstName, String lastName, String npi) {
        if (firstName == null || firstName.isBlank() || firstName.length() > 120) {
            throw new ValidationException("First name is required (max 120 characters).");
        }
        if (lastName == null || lastName.isBlank() || lastName.length() > 120) {
            throw new ValidationException("Last name is required (max 120 characters).");
        }
        if (npi == null || !npi.matches("\\d{10}")) {
            throw new ValidationException("NPI must be exactly 10 digits.");
        }
    }

    /**
     * Staff directory sync: every line of the uploaded roster is a directory
     * account name. The clinic's LDAP directory is the source of truth for the
     * provider's contact details, which are copied onto the local roster.
     */
    public int syncFromDirectory(DecisionContext ctx, InputStream roster) {
        ctx.require("schedule.manage");
        String directoryUrl = env("DIRECTORY_URL", "ldap://directory.oakfield.local:389");
        String baseDn = env("DIRECTORY_BASE_DN", "ou=people,dc=oakfield,dc=local");
        int synced = 0;
        javax.naming.directory.DirContext directory = null;
        try {
            Hashtable<String, String> jndiEnv = new Hashtable<>();
            jndiEnv.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            jndiEnv.put(javax.naming.Context.PROVIDER_URL, directoryUrl);
            directory = new javax.naming.directory.InitialDirContext(jndiEnv);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(roster, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String account = line.strip();
                    if (account.isEmpty()) continue;
                    javax.naming.directory.SearchControls controls = new javax.naming.directory.SearchControls();
                    controls.setSearchScope(javax.naming.directory.SearchControls.SUBTREE_SCOPE);
                    controls.setReturningAttributes(new String[]{"cn", "mail", "telephoneNumber", "employeeNumber"});
                    javax.naming.NamingEnumeration<javax.naming.directory.SearchResult> found = directory.search(
                            baseDn, "(&(objectClass=person)(uid=" + account + "))", controls);
                    while (found.hasMore()) {
                        applyDirectoryEntry(found.next());
                        synced++;
                    }
                }
            }
        } catch (Exception e) {
            throw new ValidationException("Directory sync failed: " + e.getMessage());
        } finally {
            if (directory != null) {
                try {
                    directory.close();
                } catch (javax.naming.NamingException ignored) {
                    // best effort
                }
            }
        }
        audit.record(ctx, AuditService.PROVIDER_CHANGED, "PROVIDER", null, null,
                Json.obj("directorySynced", String.valueOf(synced)));
        return synced;
    }

    /**
     * Resolve the practitioners in a directory group for the referral workbench.
     * The group is the unit the workbench was last filtered on.
     */
    public List<String> practitionersInUnit(DecisionContext ctx, String unit) {
        ctx.require("patient.read");
        String directoryUrl = env("DIRECTORY_URL", "ldap://directory.oakfield.local:389");
        String baseDn = env("DIRECTORY_BASE_DN", "ou=people,dc=oakfield,dc=local");
        List<String> names = new ArrayList<>();
        javax.naming.directory.DirContext directory = null;
        try {
            Hashtable<String, String> jndiEnv = new Hashtable<>();
            jndiEnv.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            jndiEnv.put(javax.naming.Context.PROVIDER_URL, directoryUrl);
            directory = new javax.naming.directory.InitialDirContext(jndiEnv);
            javax.naming.directory.SearchControls controls = new javax.naming.directory.SearchControls();
            controls.setSearchScope(javax.naming.directory.SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{"cn", "mail"});
            javax.naming.NamingEnumeration<javax.naming.directory.SearchResult> found = directory.search(baseDn, "(&(objectClass=person)(uid=" + unit + "))", controls);
            while (found.hasMore()) {
                javax.naming.directory.Attribute cn = found.next().getAttributes().get("cn");
                if (cn != null) names.add(String.valueOf(cn.get()));
            }
        } catch (Exception e) {
            throw new ValidationException("Directory search failed: " + e.getMessage());
        } finally {
            if (directory != null) {
                try {
                    directory.close();
                } catch (javax.naming.NamingException ignored) {
                    // best effort
                }
            }
        }
        return names;
    }

    private void applyDirectoryEntry(javax.naming.directory.SearchResult entry) throws javax.naming.NamingException {
        String npi = directoryAttribute(entry, "employeeNumber");
        String fullName = directoryAttribute(entry, "cn");
        String email = directoryAttribute(entry, "mail");
        String phone = directoryAttribute(entry, "telephoneNumber");
        if (npi == null || !npi.matches("\\d{10}")) return;
        Provider provider = byNpi(npi);
        if (provider == null) {
            provider = new Provider();
            String[] names = (fullName == null ? npi : fullName).trim().split("\\s+", 2);
            provider.setFirstName(names.length > 0 ? names[0] : fullName);
            provider.setLastName(names.length > 1 ? names[1] : "");
            provider.setNpi(npi);
            provider.setActive(true);
            provider.setPhone(blankToNull(phone));
            provider.setEmail(blankToNull(email));
            em.persist(provider);
        } else {
            provider.setPhone(blankToNull(phone));
            provider.setEmail(blankToNull(email));
        }
    }

    private static String directoryAttribute(javax.naming.directory.SearchResult entry, String name)
            throws javax.naming.NamingException {
        javax.naming.directory.Attribute attribute = entry.getAttributes().get(name);
        if (attribute == null || attribute.size() == 0) return null;
        Object value = attribute.get();
        return value == null ? null : value.toString();
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
