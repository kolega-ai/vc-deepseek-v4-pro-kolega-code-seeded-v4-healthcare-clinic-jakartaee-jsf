package com.oakfield.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Append-only audit event. The service exposes an insert path only; there is
 * no update or delete anywhere in the application, so no role can modify or
 * remove an audit record through any screen, endpoint, or export.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 120)
    private String actorName;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(name = "before_value", length = 4000)
    private String beforeValue;

    @Column(name = "after_value", length = 4000)
    private String afterValue;

    @Column(name = "authz_basis", length = 500)
    private String authzBasis;

    @Column(name = "request_id", length = 40)
    private String requestId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }
    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }
    public String getAuthzBasis() { return authzBasis; }
    public void setAuthzBasis(String authzBasis) { this.authzBasis = authzBasis; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
