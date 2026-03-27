package com.wallet.core.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wallet_account")
public class WalletAccount {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private BigDecimal cachedBalance = BigDecimal.ZERO;
    
    @Column(nullable = false)
    private String status = "ACTIVE";

    public WalletAccount() {}

    public WalletAccount(UUID id, UUID userId, BigDecimal cachedBalance, String status) {
        this.id = id;
        this.userId = userId;
        this.cachedBalance = cachedBalance;
        this.status = status;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public BigDecimal getCachedBalance() { return cachedBalance; }
    public void setCachedBalance(BigDecimal cachedBalance) { this.cachedBalance = cachedBalance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
