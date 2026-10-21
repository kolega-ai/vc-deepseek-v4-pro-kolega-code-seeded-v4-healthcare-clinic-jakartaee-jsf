package com.oakfield.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Demo receiver for outbound webhook deliveries so the workflow is observable locally. */
@Entity
@Table(name = "webhook_echo")
public class WebhookEcho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(length = 200)
    private String signature;

    @Column(length = 8000)
    private String payload;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
