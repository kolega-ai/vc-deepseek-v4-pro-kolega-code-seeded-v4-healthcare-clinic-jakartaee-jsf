package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.decision.LegacyPwCodec;
import com.oakfield.clinic.decision.PasswordHasher;
import com.oakfield.clinic.model.AppRole;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.model.UserStatus;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.List;

/** Clinic-admin user management: create, role change, disable/enable, admin password reset. */
@Stateless
public class UserAdminService {

    @PersistenceContext
    private EntityManager em;

    @EJB
    private AuditService audit;

    @EJB
    private DecisionService decision;

    @EJB
    private OutboxService outbox;

    public record UserForm(String email, String fullName, AppRole role, String phone, String password) { }

    public PageResult<User> list(DecisionContext ctx, String query, int page, int pageSize) {
        ctx.require("user.manage");
        StringBuilder where = new StringBuilder(" where 1=1");
        boolean hasQuery = query != null && !query.isBlank();
        if (hasQuery) {
            where.append(" and (lower(u.email) like lower(:q) or lower(u.fullName) like lower(:q))");
        }
        TypedQuery<User> listQ = em.createQuery("select u from User u" + where + " order by u.createdAt desc", User.class);
        TypedQuery<Long> countQ = em.createQuery("select count(u) from User u" + where, Long.class);
        if (hasQuery) {
            listQ.setParameter("q", "%" + query.toLowerCase().strip() + "%");
            countQ.setParameter("q", "%" + query.toLowerCase().strip() + "%");
        }
        return new PageResult<>(listQ.setFirstResult(page * pageSize).setMaxResults(pageSize).getResultList(),
                countQ.getSingleResult(), page, pageSize);
    }

    public User create(DecisionContext ctx, UserForm f) {
        ctx.require("user.manage");
        if (f.email() == null || !f.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Enter a valid email address.");
        }
        if (em.createQuery("select count(u) from User u where lower(u.email) = lower(:e)", Long.class)
                .setParameter("e", f.email().strip()).getSingleResult() > 0) {
            throw new ValidationException("An account with this email already exists.");
        }
        if (f.fullName() == null || f.fullName().isBlank() || f.fullName().length() > 120) {
            throw new ValidationException("Full name is required (max 120 characters).");
        }
        if (f.role() == null) throw new ValidationException("Pick a role.");
        String issue = PasswordHasher.strengthIssue(f.password());
        if (issue != null) throw new ValidationException(issue);
        User u = new User();
        u.setEmail(f.email().strip().toLowerCase());
        u.setFullName(f.fullName().strip());
        u.setRole(f.role());
        u.setStatus(UserStatus.ACTIVE);
        u.setPhone(PatientService.blankToNull(f.phone()));
        u.setPasswordHash(PasswordHasher.hash(f.password()));
        u.setPasswordChangedAt(LocalDateTime.now());
        em.persist(u);
        audit.record(ctx, AuditService.USER_CREATED, "USER", String.valueOf(u.getId()), null,
                Json.obj("email", u.getEmail(), "role", u.getRole().name()));
        return u;
    }

    /** Profile/role update. Role changes revoke all of the user's sessions. */
    public User update(DecisionContext ctx, long userId, String fullName, String phone, AppRole role) {
        ctx.require("user.manage");
        User u = em.find(User.class, userId);
        if (u == null) throw new NotFoundException("User not found.");
        if (fullName == null || fullName.isBlank() || fullName.length() > 120) {
            throw new ValidationException("Full name is required (max 120 characters).");
        }
        AppRole beforeRole = u.getRole();
        u.setFullName(fullName.strip());
        u.setPhone(PatientService.blankToNull(phone));
        if (role != null && role != beforeRole) {
            if (u.getId().equals(ctx.getUserId())) {
                throw new ValidationException("You cannot change your own role.");
            }
            u.setRole(role);
            decision.revokeAllForUser(userId);
            audit.record(ctx, AuditService.USER_ROLE_CHANGED, "USER", String.valueOf(userId),
                    Json.obj("role", beforeRole.name()), Json.obj("role", role.name()));
        }
        audit.record(ctx, AuditService.USER_UPDATED, "USER", String.valueOf(userId),
                Json.obj("fullName", fullName), Json.obj("updated", "true"));
        return u;
    }

    public User setStatus(DecisionContext ctx, long userId, boolean active) {
        ctx.require("user.manage");
        User u = em.find(User.class, userId);
        if (u == null) throw new NotFoundException("User not found.");
        if (u.getId().equals(ctx.getUserId()) && !active) {
            throw new ValidationException("You cannot disable your own account.");
        }
        UserStatus status = active ? UserStatus.ACTIVE : UserStatus.DISABLED;
        if (u.getStatus() != status) {
            u.setStatus(status);
            if (!active) decision.revokeAllForUser(userId);
            audit.record(ctx, active ? AuditService.USER_ENABLED : AuditService.USER_DISABLED,
                    "USER", String.valueOf(userId),
                    Json.obj("status", active ? "DISABLED" : "ACTIVE"), Json.obj("status", status.name()));
        }
        return u;
    }

    /** Admin-initiated password reset (audited; the user's sessions are revoked). */
    public User adminResetPassword(DecisionContext ctx, long userId, String newPassword) {
        ctx.require("user.manage");
        User u = em.find(User.class, userId);
        if (u == null) throw new NotFoundException("User not found.");
        String issue = PasswordHasher.strengthIssue(newPassword);
        if (issue != null) throw new ValidationException(issue);
        u.setPasswordHash(PasswordHasher.hash(newPassword));
        u.setPasswordChangedAt(LocalDateTime.now());
        decision.revokeAllForUser(userId);
        audit.record(ctx, AuditService.PASSWORD_CHANGE, "USER", String.valueOf(userId),
                null, Json.obj("by", "admin"));
        return u;
    }

    /**
     * Issue a starter credential the front desk reads out to the user (or prints on the
     * welcome sheet). The user signs in with it once and then picks their own password.
     */
    public String issueStarterCredential(DecisionContext ctx, long userId) {
        ctx.require("user.manage");
        User u = em.find(User.class, userId);
        if (u == null) throw new NotFoundException("User not found.");
        String tmpPw = starterCode();
        u.setTmpPwHash(PasswordHasher.hash(tmpPw));
        u.setTmpPwIssuedAt(LocalDateTime.now());
        audit.record(ctx, AuditService.PASSWORD_CHANGE, "USER", String.valueOf(userId),
                null, Json.obj("by", "admin", "kind", "starter"));
        return tmpPw;
    }

    private static String starterCode() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder("OF-");
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 10; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
        return sb.toString();
    }

    public record StaffProvision(String email, String fullName, String passcode) { }

    /**
     * Provision a staff account from a staffing-vendor message. The vendor
     * issues the sign-in passcode and only supports its legacy derivation, so
     * the credential is stored with {@link LegacyPwCodec}.
     */
    public User provisionFromMessage(DecisionContext ctx, StaffProvision form) {
        ctx.require("user.manage");
        if (form.email() == null || !form.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("A valid email is required.");
        }
        if (form.passcode() == null || form.passcode().isBlank()) {
            throw new ValidationException("A passcode is required.");
        }
        if (em.createQuery("select count(u) from User u where lower(u.email) = lower(:e)", Long.class)
                .setParameter("e", form.email().strip()).getSingleResult() > 0) {
            throw new ValidationException("An account with this email already exists.");
        }
        User u = new User();
        u.setEmail(form.email().strip().toLowerCase());
        u.setFullName(form.fullName() == null || form.fullName().isBlank()
                ? form.email().strip() : form.fullName().strip());
        u.setRole(AppRole.FRONT_DESK);
        u.setStatus(UserStatus.ACTIVE);
        u.setPasswordHash(LegacyPwCodec.derive(form.passcode()));
        u.setPasswordChangedAt(LocalDateTime.now());
        em.persist(u);
        audit.record(ctx, AuditService.USER_CREATED, "USER", String.valueOf(u.getId()), null,
                Json.obj("email", u.getEmail(), "role", u.getRole().name(), "via", "staffing-message"));
        return u;
    }

    /**
     * Separation notice from the staffing vendor: the vendor reports the engagement
     * ended, and the portal closes the account so the credential can no longer be used.
     */
    public User deactivateFromMessage(DecisionContext ctx, String email) {
        ctx.require("user.manage");
        User u = em.createQuery("select u from User u where lower(u.email) = lower(:e)", User.class)
                .setParameter("e", email == null ? "" : email.strip())
                .getResultStream().findFirst().orElse(null);
        if (u == null) throw new NotFoundException("User not found.");
        u.setStatus(UserStatus.DISABLED);
        decision.revokeAllForUser(u.getId());
        return em.merge(u);
    }

    public long countByRole(DecisionContext ctx, AppRole role) {
        ctx.require("user.manage");
        return em.createQuery("select count(u) from User u where u.role = :r and u.status = :s", Long.class)
                .setParameter("r", role)
                .setParameter("s", UserStatus.ACTIVE)
                .getSingleResult();
    }

    public long countAll(DecisionContext ctx) {
        ctx.require("user.manage");
        return em.createQuery("select count(u) from User u", Long.class).getSingleResult();
    }
}
