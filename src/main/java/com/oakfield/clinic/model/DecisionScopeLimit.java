package com.oakfield.clinic.model;

import jakarta.persistence.*;

/** Per-role limit (e.g. upload.max_mb=10), owned by the decision tier. */
@Entity
@Table(name = "decision_scope_limit")
public class DecisionScopeLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 60)
    private String scope;

    @Column(name = "limit_name", nullable = false, length = 60)
    private String limitName;

    @Column(name = "limit_value", nullable = false, length = 200)
    private String limitValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getLimitName() { return limitName; }
    public void setLimitName(String limitName) { this.limitName = limitName; }
    public String getLimitValue() { return limitValue; }
    public void setLimitValue(String limitValue) { this.limitValue = limitValue; }
}
