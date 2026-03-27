package com.wallet.tx.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId; // Corresponds to Wallet userId

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private String type; // DEBIT / CREDIT

    @Column(nullable = false)
    private BigDecimal amount;

    private LocalDateTime createdAt = LocalDateTime.now();
    
    private String description;

    public LedgerEntry() {}

    public LedgerEntry(UUID id, UUID accountId, UUID transactionId, String type, BigDecimal amount, LocalDateTime createdAt, String description) {
        this.id = id;
        this.accountId = accountId;
        this.transactionId = transactionId;
        this.type = type;
        this.amount = amount;
        this.createdAt = createdAt;
        this.description = description;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
