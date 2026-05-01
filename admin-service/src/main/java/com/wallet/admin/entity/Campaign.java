package com.wallet.admin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
public class Campaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @NotBlank(message = "Campaign name is required")
    private String name;

    private String targetTier; // e.g. GOLD

    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    private Integer rewardPoints;

    private String triggerEvent = "FIRST_TRANSACTION";

    private LocalDateTime createdAt = LocalDateTime.now();

    public Campaign() {}

    public Campaign(UUID id, String name, String targetTier, String status, Integer rewardPoints, String triggerEvent, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.targetTier = targetTier;
        this.status = status;
        this.rewardPoints = rewardPoints;
        this.triggerEvent = triggerEvent;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetTier() { return targetTier; }
    public void setTargetTier(String targetTier) { this.targetTier = targetTier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }
    public String getTriggerEvent() { return triggerEvent; }
    public void setTriggerEvent(String triggerEvent) { this.triggerEvent = triggerEvent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
