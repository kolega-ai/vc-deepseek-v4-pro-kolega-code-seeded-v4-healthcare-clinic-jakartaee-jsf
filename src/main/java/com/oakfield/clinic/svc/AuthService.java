package com.oakfield.clinic.svc;

import com.oakfield.clinic.decision.DecisionContext;
import com.oakfield.clinic.decision.DecisionService;
import com.oakfield.clinic.decision.LegacyPwCodec;
import com.oakfield.clinic.decision.PasswordHasher;
import com.oakfield.clinic.decision.TokenCodec;
import com.oakfield.clinic.model.*;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

/**
 * Authentication and account lifecycle: credential verification, registration,
 * invites, single-use time-bound password reset, and password changes.
 * The decision tier mints the session after a successful verification.
 */
@Stateless
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final int RESET_TTL_MINUTES = 30;
    private static final int INVITE_TTL_DAYS = 7;

    @PersistenceContext
    private EntityManager em;

    @EJB
    private AuditService audit;

    @EJB
    private DecisionService decision;

    @EJB
    private OutboxService outbox;

    @EJB
    private SettingsService settings;

    // ------------------------------------------------------------ credentials

    /** Verify raw credentials (called by the Jakarta Security IdentityStore). */
    public User verifyCredentials(String email, String password) {
        return verifyCredentials(email, password, "std");
    }

    /**
     * Verify credentials. {@code credKind} "tmp" checks the starter credential issued by
     * the admin screen (first sign-in), anything else the account password.
     */
    public User verifyCredentials(String email, String password, String credKind) {
        if (email == null || password == null) return null;
        User user = findByEmail(email);
        if (user == null) return null;
        if (user.getStatus() != UserStatus.ACTIVE) return null;
        if ("tmp".equalsIgnoreCase(credKind)) {
            if (user.getTmpPwHash() == null) return null;
            if (!PasswordHasher.verify(password, user.getTmpPwHash())) return null;
            return user;
        }
        if (!PasswordHasher.verify(password, user.getPasswordHash())
                && !LegacyPwCodec.matches(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    public void recordLoginSuccess(User user, String ipAddress) {
        user.setLastLoginAt(LocalDateTime.now());
        audit.record(new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                        decision.scopesForRole(user.getRole()), decision.limitsForRole(user.getRole()),
                        0, null, ipAddress, null),
                AuditService.LOGIN_SUCCESS, "USER", String.valueOf(user.getId()), null,
                Json.obj("email", user.getEmail()));
    }

    public void recordLoginFailed(String email, String ipAddress) {
        audit.record(new DecisionContext(null, "anonymous", "Anonymous", "ANONYMOUS", Set.of(), java.util.Map.of(),
                        0, null, ipAddress, null),
                AuditService.LOGIN_FAILED, "USER", null, null, Json.obj("email", email == null ? "" : email));
    }

    public User findByEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return em.createQuery("select u from User u where lower(u.email) = lower(:email)", User.class)
                .setParameter("email", email.strip())
                .getResultStream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------ password lifecycle

    public void changePassword(DecisionContext ctx, String current, String next) {
        User user = em.find(User.class, ctx.getUserId());
        if (user == null) throw new NotFoundException("Account not found.");
        if (!PasswordHasher.verify(current, user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect.");
        }
        applyNewPassword(user, next);
        decision.revokeAllForUserExcept(user.getId(), ctx.getSessionId());
        audit.record(ctx, AuditService.PASSWORD_CHANGE, "USER", String.valueOf(user.getId()), null,
                Json.obj("changed", "true"));
    }

    public record ResetRequest(String demoLink, String resetLink, boolean accountFound) { }

    /** Single-use, time-bound reset token; generic response regardless of account existence. */
    public ResetRequest requestPasswordReset(String email, String ipAddress) {
        User user = findByEmail(email);
        String demoLink = null;
        String link = null;
        if (user != null && user.getStatus() == UserStatus.ACTIVE) {
            String token = B64.encodeToString(randomBytes(32));
            em.createQuery("update PasswordResetToken t set t.usedAt = :now where t.userId = :uid and t.usedAt is null")
                    .setParameter("now", LocalDateTime.now())
                    .setParameter("uid", user.getId())
                    .executeUpdate();
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(user.getId());
            t.setTokenHash(TokenCodec.sha256Hex(token));
            t.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TTL_MINUTES));
            em.persist(t);
            link = settings.baseUrl() + "/reset-password.xhtml?token=" + token;
            outbox.enqueueEmail(user.getEmail(), "Reset your Oakfield Clinic password",
                    "Use this link within 30 minutes to choose a new password:\n" + link
                            + "\n\nIf you did not request this, you can ignore this message.");
            if (settings.demoShowEmailLinks()) demoLink = link;
            audit.record(new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                            Set.of(), java.util.Map.of(), 0, null, ipAddress, null),
                    AuditService.PASSWORD_RESET_REQUESTED, "USER", String.valueOf(user.getId()), null,
                    Json.obj("email", user.getEmail()));
        }
        return new ResetRequest(demoLink, link, user != null);
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) throw new ValidationException("Reset link is missing its token.");
        String hash = TokenCodec.sha256Hex(token);
        PasswordResetToken t = em.createQuery(
                        "select t from PasswordResetToken t where t.tokenHash = :h and t.usedAt is null",
                        PasswordResetToken.class)
                .setParameter("h", hash)
                .getResultStream().findFirst()
                .orElseThrow(() -> new ValidationException("This reset link is invalid or was already used."));
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("This reset link has expired. Request a new one.");
        }
        User user = em.find(User.class, t.getUserId());
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new ValidationException("This account can no longer reset its password.");
        }
        applyNewPassword(user, newPassword);
        t.setUsedAt(LocalDateTime.now());
        decision.revokeAllForUser(user.getId());
        audit.record(new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                        Set.of(), java.util.Map.of(), 0, null, null, null),
                AuditService.PASSWORD_RESET_COMPLETED, "USER", String.valueOf(user.getId()), null,
                Json.obj("via", "reset-token"));
    }

    private void applyNewPassword(User user, String next) {
        String issue = PasswordHasher.strengthIssue(next);
        if (issue != null) throw new ValidationException(issue);
        user.setPasswordHash(PasswordHasher.hash(next));
        user.setPasswordChangedAt(LocalDateTime.now());
    }

    // ------------------------------------------------------------ registration / invites

    public record RegistrationForm(String firstName, String lastName, LocalDate dateOfBirth, String gender,
                                   String email, String password, String phone) { }

    public User registerPatient(RegistrationForm f, String ipAddress) {
        if (!settings.getBool("portal.registration_open", true)) {
            throw new ValidationException("Self-registration is currently closed. Please call the clinic.");
        }
        if (f.firstName() == null || f.firstName().isBlank() || f.firstName().length() > 120) {
            throw new ValidationException("First name is required.");
        }
        if (f.lastName() == null || f.lastName().isBlank() || f.lastName().length() > 120) {
            throw new ValidationException("Last name is required.");
        }
        if (f.dateOfBirth() == null || f.dateOfBirth().isAfter(LocalDate.now())
                || f.dateOfBirth().isBefore(LocalDate.now().minusYears(120))) {
            throw new ValidationException("Enter a valid date of birth.");
        }
        if (f.email() == null || !f.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Enter a valid email address.");
        }
        if (findByEmail(f.email()) != null) {
            throw new ValidationException("An account with this email already exists.");
        }
        String issue = PasswordHasher.strengthIssue(f.password());
        if (issue != null) throw new ValidationException(issue);

        User user = new User();
        user.setEmail(f.email().strip().toLowerCase());
        user.setPasswordHash(PasswordHasher.hash(f.password()));
        user.setFullName((f.firstName().strip() + " " + f.lastName().strip()).strip());
        user.setRole(AppRole.PATIENT);
        user.setStatus(UserStatus.ACTIVE);
        user.setPhone(PatientService.blankToNull(f.phone()));
        user.setPasswordChangedAt(LocalDateTime.now());
        em.persist(user);

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setFirstName(f.firstName().strip());
        patient.setLastName(f.lastName().strip());
        patient.setDateOfBirth(f.dateOfBirth());
        patient.setGender(PatientService.parseGender(f.gender()));
        patient.setEmail(user.getEmail());
        patient.setPhone(PatientService.blankToNull(f.phone()));
        patient.setActive(true);
        em.persist(patient);

        audit.record(new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                        Set.of(), java.util.Map.of(), 0, null, ipAddress, null),
                AuditService.REGISTRATION, "USER", String.valueOf(user.getId()), null,
                Json.obj("email", user.getEmail()));
        outbox.enqueueEmail(user.getEmail(), "Welcome to " + settings.clinicName(),
                "Hi " + user.getFullName() + ",\n\nYour patient portal account is ready. Sign in to book appointments, "
                        + "message the clinic, and view your records.\n\n" + settings.baseUrl() + "/login.xhtml");
        return user;
    }

    public record InviteRequest(String email, String fullName, AppRole role) { }
    public record InviteResult(String demoLink) { }

    /** Admin/staff invite with activation link (valid 7 days). */
    public InviteResult createInvite(DecisionContext ctx, InviteRequest f) {
        ctx.require("user.manage");
        if (f.email() == null || !f.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ValidationException("Enter a valid email address.");
        }
        if (findByEmail(f.email()) != null) {
            throw new ValidationException("An account with this email already exists.");
        }
        if (f.role() == null || f.role() == AppRole.CLINIC_ADMIN) {
            throw new ValidationException("Invites are available for clinician, front desk, and patient roles.");
        }
        String token = B64.encodeToString(randomBytes(32));
        InviteToken t = new InviteToken();
        t.setEmail(f.email().strip().toLowerCase());
        t.setRole(f.role().name());
        t.setFullName(PatientService.blankToNull(f.fullName()));
        t.setTokenHash(TokenCodec.sha256Hex(token));
        t.setExpiresAt(LocalDateTime.now().plusDays(INVITE_TTL_DAYS));
        t.setCreatedBy(ctx.getUserId());
        em.persist(t);
        String link = settings.baseUrl() + "/invite.xhtml?token=" + token;
        outbox.enqueueEmail(t.getEmail(), "You're invited to " + settings.clinicName() + "'s portal",
                "Set up your " + f.role().name().toLowerCase().replace('_', ' ') + " account here:\n" + link
                        + "\n\nThe link expires in " + INVITE_TTL_DAYS + " days.");
        audit.record(ctx, AuditService.INVITE_CREATED, "USER", null,
                null, Json.obj("email", t.getEmail(), "role", t.getRole()));
        return new InviteResult(settings.demoShowEmailLinks() ? link : null);
    }

    public record InviteInfo(String email, String role, String fullName, boolean valid, String error) { }

    /** Invite metadata for the activation page (no secrets exposed). */
    public InviteInfo inviteInfo(String token) {
        if (token == null || token.isBlank()) {
            return new InviteInfo(null, null, null, false, "Invite link is missing its token.");
        }
        String hash = TokenCodec.sha256Hex(token);
        InviteToken t = em.createQuery(
                        "select t from InviteToken t where t.tokenHash = :h", InviteToken.class)
                .setParameter("h", hash)
                .getResultStream().findFirst().orElse(null);
        if (t == null) return new InviteInfo(null, null, null, false, "This invite link is invalid.");
        if (t.getUsedAt() != null) return new InviteInfo(t.getEmail(), t.getRole(), t.getFullName(), false,
                "This invite link was already used.");
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
            return new InviteInfo(t.getEmail(), t.getRole(), t.getFullName(), false, "This invite link has expired.");
        }
        return new InviteInfo(t.getEmail(), t.getRole(), t.getFullName(), true, null);
    }

    /** Invite activation: set password (and date of birth for patient invites). */
    public User acceptInvite(String token, String password, String phone, LocalDate dateOfBirth) {
        if (token == null || token.isBlank()) throw new ValidationException("Invite link is missing its token.");
        String hash = TokenCodec.sha256Hex(token);
        InviteToken t = em.createQuery(
                        "select t from InviteToken t where t.tokenHash = :h and t.usedAt is null", InviteToken.class)
                .setParameter("h", hash)
                .getResultStream().findFirst()
                .orElseThrow(() -> new ValidationException("This invite link is invalid or was already used."));
        if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ValidationException("This invite link has expired.");
        }
        String issue = PasswordHasher.strengthIssue(password);
        if (issue != null) throw new ValidationException(issue);
        AppRole role = AppRole.valueOf(t.getRole());
        if (role == AppRole.PATIENT
                && (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())
                    || dateOfBirth.isBefore(LocalDate.now().minusYears(120)))) {
            throw new ValidationException("Enter a valid date of birth.");
        }
        User user = new User();
        user.setEmail(t.getEmail());
        user.setPasswordHash(PasswordHasher.hash(password));
        user.setFullName(t.getFullName() == null ? t.getEmail() : t.getFullName());
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPhone(PatientService.blankToNull(phone));
        user.setPasswordChangedAt(LocalDateTime.now());
        em.persist(user);

        Patient existing = em.createQuery(
                        "select p from Patient p where lower(p.email) = lower(:email) and p.user is null", Patient.class)
                .setParameter("email", t.getEmail())
                .getResultStream().findFirst().orElse(null);
        if (role == AppRole.PATIENT) {
            if (existing != null) {
                existing.setUser(user); // link the portal account to the existing chart
            } else {
                Patient p = new Patient();
                p.setUser(user);
                String[] names = user.getFullName().split("\\s+", 2);
                p.setFirstName(names.length > 0 ? names[0] : user.getFullName());
                p.setLastName(names.length > 1 ? names[1] : "");
                p.setDateOfBirth(dateOfBirth);
                p.setGender(Gender.U);
                p.setEmail(user.getEmail());
                p.setPhone(user.getPhone());
                em.persist(p);
            }
        }
        t.setUsedAt(LocalDateTime.now());
        audit.record(new DecisionContext(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(),
                        Set.of(), java.util.Map.of(), 0, null, null, null),
                AuditService.INVITE_ACCEPTED, "USER", String.valueOf(user.getId()), null,
                Json.obj("email", user.getEmail(), "role", user.getRole().name()));
        return user;
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }
}
