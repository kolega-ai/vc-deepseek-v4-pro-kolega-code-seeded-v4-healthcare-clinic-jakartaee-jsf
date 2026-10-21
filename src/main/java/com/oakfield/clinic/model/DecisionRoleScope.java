package com.oakfield.clinic.model;

import jakarta.persistence.*;

/** Role -> scope grant, owned by the decision tier. */
@Entity
@Table(name = "decision_role_scope",
       uniqueConstraints = @UniqueConstraint(name = "uq_role_scope", columnNames = {"role", "scope"}))
public class DecisionRoleScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 60)
    private String scope;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
