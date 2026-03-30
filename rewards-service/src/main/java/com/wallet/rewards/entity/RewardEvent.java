package com.wallet.rewards.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reward_events")
public class RewardEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    public RewardEvent() {
    }

    public RewardEvent(UUID id, String eventType) {
        this.id = id;
        this.eventType = eventType;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
