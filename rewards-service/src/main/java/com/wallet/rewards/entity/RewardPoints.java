package com.wallet.rewards.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reward_points")
public class RewardPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    private int totalPoints = 0;

    @Column(nullable = false)
    private String tier = "SILVER"; // SILVER, GOLD, PLATINUM

    private LocalDateTime lastUpdated = LocalDateTime.now();

    public RewardPoints() {}

    public RewardPoints(UUID id, UUID userId, int totalPoints, String tier, LocalDateTime lastUpdated) {
        this.id = id;
        this.userId = userId;
        this.totalPoints = totalPoints;
        this.tier = tier;
        this.lastUpdated = lastUpdated;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public void addPoints(int points) {
        this.totalPoints += points;
        this.lastUpdated = LocalDateTime.now();
        updateTier();
    }
    
    public void deductPoints(int points) {
        this.totalPoints -= points;
        this.lastUpdated = LocalDateTime.now();
        updateTier();
    }
    
    private void updateTier() {
        if (this.totalPoints >= 5000) {
            this.tier = "PLATINUM";
        } else if (this.totalPoints >= 1000) {
            this.tier = "GOLD";
        } else {
            this.tier = "SILVER";
        }
    }
}
