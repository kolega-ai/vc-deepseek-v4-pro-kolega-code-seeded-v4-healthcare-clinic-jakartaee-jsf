package com.oakfield.clinic.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "provider_blocked_date",
       uniqueConstraints = @UniqueConstraint(name = "uq_provider_block", columnNames = {"provider_id", "block_date"}))
public class ProviderBlockedDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    @Column(length = 200)
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public LocalDate getBlockDate() { return blockDate; }
    public void setBlockDate(LocalDate blockDate) { this.blockDate = blockDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
