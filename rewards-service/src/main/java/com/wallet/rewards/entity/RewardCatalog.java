package com.wallet.rewards.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Entity
@Table(name = "reward_catalog")
public class RewardCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @NotBlank(message = "Item name is required")
    private String name;

    @Column(nullable = false)
    @NotBlank(message = "Description is required")
    private String description;

    @Column(nullable = false)
    @Positive(message = "Cost in points must be positive")
    private int costInPoints;

    private int stockQuantity;

    private String requiredTier; // e.g. GOLD, PLATINUM, or ALL

    public RewardCatalog() {}

    public RewardCatalog(UUID id, String name, String description, int costInPoints, int stockQuantity, String requiredTier) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.costInPoints = costInPoints;
        this.stockQuantity = stockQuantity;
        this.requiredTier = requiredTier;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getCostInPoints() { return costInPoints; }
    public void setCostInPoints(int costInPoints) { this.costInPoints = costInPoints; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public String getRequiredTier() { return requiredTier; }
    public void setRequiredTier(String requiredTier) { this.requiredTier = requiredTier; }
}
