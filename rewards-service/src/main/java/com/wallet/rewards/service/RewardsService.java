package com.wallet.rewards.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.rewards.entity.RewardCatalog;
import com.wallet.rewards.entity.RewardEvent;
import com.wallet.rewards.entity.RewardPoints;
import com.wallet.rewards.repository.RewardCatalogRepository;
import com.wallet.rewards.repository.RewardEventRepository;
import com.wallet.rewards.repository.RewardPointsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RewardsService {

    private static final Logger log = LoggerFactory.getLogger(RewardsService.class);

    @Autowired
    private RewardPointsRepository pointsRepository;

    @Autowired
    private RewardCatalogRepository catalogRepository;

    @Autowired
    private RewardEventRepository rewardEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    // 1 point for every 100 spent or topped up
    private int calculatePoints(BigDecimal amount) {
        return amount.divideToIntegralValue(new BigDecimal("100")).intValue();
    }

    private RewardPoints getOrCreatePoints(UUID userId) {
        // Native upsert keeps reward records idempotent when events arrive for a first-time user.
        pointsRepository.ensureUserExists(userId);
        return pointsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Rewards record should exist for user: " + userId));
    }

    @KafkaListener(topics = "wallet.topup.success", groupId = "rewards-group")
    @Transactional
    public void handleTopUp(Map<String, Object> event) {
        log.info("Rewards Service received Top-Up Event: {}", event);
        try {
            applyTopUpRewards(event);
        } catch (Exception e) {
            log.error("Rewards Service Top-Up error: {}", event, e);
        }
    }

    @KafkaListener(topics = "wallet.transfer.completed", groupId = "rewards-group")
    @Transactional
    public void handleTransfer(Map<String, Object> event) {
        log.info("Rewards Service received Transfer Event: {}", event);
        try {
            applyTransferRewards(event);
        } catch (Exception e) {
            log.error("Rewards Service Transfer error: {}", event, e);
        }
    }

    @Transactional
    public void applyTopUpRewards(Map<String, Object> event) {
        UUID txId = UUID.fromString(event.get("transactionId").toString());
        if (rewardEventRepository.existsById(txId)) {
            log.info("Skipping duplicate reward processing for top-up transaction {}", txId);
            return;
        }

        UUID userId = UUID.fromString(event.get("userId").toString());
        BigDecimal amount = new BigDecimal(event.get("amount").toString());

        int points = calculatePoints(amount);
        if (points > 0) {
            RewardPoints rp = getOrCreatePoints(userId);
            rp.addPoints(points);
            pointsRepository.saveAndFlush(rp);
            rewardEventRepository.saveAndFlush(new RewardEvent(txId, "TOPUP"));
            log.info("Awarded {} points to user: {}", points, userId);
        }
    }

    @Transactional
    public void applyTransferRewards(Map<String, Object> event) {
        UUID txId = UUID.fromString(event.get("transactionId").toString());
        if (rewardEventRepository.existsById(txId)) {
            log.info("Skipping duplicate reward processing for transfer transaction {}", txId);
            return;
        }

        UUID fromUserId = UUID.fromString(event.get("fromUserId").toString());
        BigDecimal amount = new BigDecimal(event.get("amount").toString());

        int points = calculatePoints(amount);
        if (points > 0) {
            RewardPoints rp = getOrCreatePoints(fromUserId);
            rp.addPoints(points);
            pointsRepository.saveAndFlush(rp);
            rewardEventRepository.saveAndFlush(new RewardEvent(txId, "TRANSFER"));
            log.info("Awarded {} points to sender: {}", points, fromUserId);
        }
    }

    @Transactional
    public RewardPoints getSummary(UUID userId) {
        return getOrCreatePoints(userId);
    }

    public List<RewardCatalog> getCatalog() {
        return catalogRepository.findAll();
    }

    @Transactional
    public String redeemItem(UUID userId, UUID catalogId) {
        RewardPoints rp = getOrCreatePoints(userId);
        RewardCatalog item = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new RuntimeException("Catalog item not found"));

        // Tier checks are intentionally simple and map directly to the three supported tiers.
        if (!"ALL".equals(item.getRequiredTier()) && !item.getRequiredTier().equals(rp.getTier())) {
            // Very simplified tier check
            if (rp.getTier().equals("SILVER") && (item.getRequiredTier().equals("GOLD") || item.getRequiredTier().equals("PLATINUM"))) {
                 throw new RuntimeException("Tier too low to redeem this item");
            }
            if (rp.getTier().equals("GOLD") && item.getRequiredTier().equals("PLATINUM")) {
                 throw new RuntimeException("Tier too low to redeem this item");
            }
        }

        if (rp.getTotalPoints() < item.getCostInPoints()) {
            throw new RuntimeException("Insufficient points");
        }

        if (item.getStockQuantity() <= 0) {
            throw new RuntimeException("Item out of stock");
        }

        rp.deductPoints(item.getCostInPoints());
        pointsRepository.save(rp);

        item.setStockQuantity(item.getStockQuantity() - 1);
        catalogRepository.save(item);

        return "Successfully redeemed " + item.getName();
    }

    @Transactional
    public RewardCatalog createCatalogItem(RewardCatalog item) {
        return catalogRepository.save(item);
    }
}
