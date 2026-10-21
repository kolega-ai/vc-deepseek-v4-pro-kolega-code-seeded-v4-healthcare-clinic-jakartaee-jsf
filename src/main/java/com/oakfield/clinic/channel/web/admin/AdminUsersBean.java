package com.oakfield.clinic.channel.web.admin;

import com.oakfield.clinic.channel.web.common.FacesUtil;
import com.oakfield.clinic.channel.web.common.SessionInfo;
import com.oakfield.clinic.model.AppRole;
import com.oakfield.clinic.model.User;
import com.oakfield.clinic.svc.AuthService;
import com.oakfield.clinic.svc.PageResult;
import com.oakfield.clinic.svc.UserAdminService;
import com.oakfield.clinic.svc.ValidationException;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/** Clinic-admin user management: list/search, create, edit, enable/disable, password reset, invites. */
@Named("adminUsers")
@ViewScoped
public class AdminUsersBean implements Serializable {

    @Inject
    private SessionInfo session;
    @Inject
    private UserAdminService users;
    @Inject
    private AuthService auth;

    private String query = "";
    private int pageIndex;

    // create form
    private String email;
    private String fullName;
    private String role = "FRONT_DESK";
    private String phone;
    private String password;

    // edit form
    private long editId = -1;
    private String editFullName;
    private String editPhone;
    private String editRole;
    private String newAdminPassword;
    private String starterCode;

    // invite form
    private String inviteEmail;
    private String inviteFullName;
    private String inviteRole = "PATIENT";
    private String demoLink;

    @PostConstruct
    public void load() {
        // list page is computed by getPage() on demand
    }

    public PageResult<User> getPage() {
        return users.list(session.ctx(), query, pageIndex, 20);
    }

    public void filter() {
        pageIndex = 0;
    }

    public void nextPage() {
        pageIndex++;
    }

    public void prevPage() {
        if (pageIndex > 0) pageIndex--;
    }

    public void createUser() {
        try {
            users.create(session.ctx(), new UserAdminService.UserForm(
                    email, fullName, parseRole(role), phone, password));
            FacesUtil.info("User created", "The account is ready to use.");
            email = null;
            fullName = null;
            phone = null;
            password = null;
            role = "FRONT_DESK";
            pageIndex = 0;
        } catch (ValidationException e) {
            FacesUtil.error("Could not create user", e.getMessage());
        }
    }

    public void startEdit(User u) {
        editId = u.getId();
        editFullName = u.getFullName();
        editPhone = u.getPhone();
        editRole = u.getRole() == null ? null : u.getRole().name();
        newAdminPassword = null;
    }

    public void cancelEdit() {
        editId = -1;
        editFullName = null;
        editPhone = null;
        editRole = null;
        newAdminPassword = null;
    }

    public boolean isEditing() {
        return editId >= 0;
    }

    public void saveEdit() {
        try {
            users.update(session.ctx(), editId, editFullName, editPhone, parseRole(editRole));
            FacesUtil.info("User updated", "Profile changes were saved.");
            cancelEdit();
        } catch (ValidationException e) {
            FacesUtil.error("Could not update user", e.getMessage());
        }
    }

    public void setActive(long id) {
        try {
            users.setStatus(session.ctx(), id, true);
            FacesUtil.info("Account enabled", "The account is active again.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not enable account", e.getMessage());
        }
    }

    public void setDisabled(long id) {
        try {
            users.setStatus(session.ctx(), id, false);
            FacesUtil.info("Account disabled", "The account is disabled and its sessions were revoked.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not disable account", e.getMessage());
        }
    }

    public void adminResetPassword() {
        try {
            users.adminResetPassword(session.ctx(), editId, newAdminPassword);
            FacesUtil.info("Password reset", "The user must sign in again with the new password.");
            newAdminPassword = null;
        } catch (ValidationException e) {
            FacesUtil.error("Could not reset password", e.getMessage());
        }
    }

    public void issueStarterCode() {
        try {
            starterCode = users.issueStarterCredential(session.ctx(), editId);
            FacesUtil.info("Starter code issued", "The user signs in with it once, then sets a password.");
        } catch (ValidationException e) {
            FacesUtil.error("Could not issue starter code", e.getMessage());
        }
    }

    public void sendInvite() {
        try {
            AuthService.InviteResult result = auth.createInvite(session.ctx(),
                    new AuthService.InviteRequest(inviteEmail, inviteFullName, parseRole(inviteRole)));
            demoLink = result.demoLink();
            FacesUtil.info("Invite sent", inviteEmail + " can now activate their account.");
            inviteEmail = null;
            inviteFullName = null;
            inviteRole = "PATIENT";
        } catch (ValidationException e) {
            FacesUtil.error("Could not send invite", e.getMessage());
        }
    }

    private static AppRole parseRole(String s) {
        if (s == null || s.isBlank()) throw new ValidationException("Pick a role.");
        try {
            return AppRole.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown role: " + s);
        }
    }

    public AppRole[] getRolesArray() {
        return AppRole.values();
    }

    public AppRole[] getInviteRolesArray() {
        return new AppRole[]{AppRole.CLINICIAN, AppRole.FRONT_DESK, AppRole.PATIENT};
    }

    // ------------------------------------------------------------ getters/setters

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getPageIndex() { return pageIndex; }
    public void setPageIndex(int pageIndex) { this.pageIndex = pageIndex; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public long getEditId() { return editId; }
    public void setEditId(long editId) { this.editId = editId; }
    public String getEditFullName() { return editFullName; }
    public void setEditFullName(String editFullName) { this.editFullName = editFullName; }
    public String getEditPhone() { return editPhone; }
    public void setEditPhone(String editPhone) { this.editPhone = editPhone; }
    public String getEditRole() { return editRole; }
    public void setEditRole(String editRole) { this.editRole = editRole; }
    public String getNewAdminPassword() { return newAdminPassword; }
    public void setNewAdminPassword(String newAdminPassword) { this.newAdminPassword = newAdminPassword; }
    public String getInviteEmail() { return inviteEmail; }
    public void setInviteEmail(String inviteEmail) { this.inviteEmail = inviteEmail; }
    public String getInviteFullName() { return inviteFullName; }
    public void setInviteFullName(String inviteFullName) { this.inviteFullName = inviteFullName; }
    public String getInviteRole() { return inviteRole; }
    public void setInviteRole(String inviteRole) { this.inviteRole = inviteRole; }
    public String getStarterCode() { return starterCode; }
    public String getDemoLink() { return demoLink; }
    public void setDemoLink(String demoLink) { this.demoLink = demoLink; }
}
