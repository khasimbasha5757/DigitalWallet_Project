package com.wallet.rewards.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.rewards.entity.RewardCatalog;
import com.wallet.rewards.entity.RewardEvent;
import com.wallet.rewards.entity.RewardPoints;
import com.wallet.rewards.repository.RewardCatalogRepository;
import com.wallet.rewards.repository.RewardEventRepository;
import com.wallet.rewards.repository.RewardPointsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Autowired
    private RestTemplate restTemplate;

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Gateway-Token";
    private static final String INTERNAL_SECRET_VALUE = "DigitalWalletInternalSecret2026";

    @Value("${wallet.internal.base-url:http://localhost:8083/api/wallet/internal}")
    private String walletInternalBaseUrl;

    @Value("${notification.internal.base-url:http://localhost:8086/api/notifications/internal}")
    private String notificationInternalBaseUrl;

    @Value("${admin.internal.base-url:http://localhost:8087/api/admin}")
    private String adminInternalBaseUrl;

    private static final Pattern REWARD_POINTS_PATTERN =
            Pattern.compile("(\\d+)\\s*(?:reward\\s*)?points?", Pattern.CASE_INSENSITIVE);

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
        RewardPoints rp = getOrCreatePoints(userId);
        if (points > 0) {
            rp.addPoints(points);
            pointsRepository.saveAndFlush(rp);
            log.info("Awarded {} points to user: {}", points, userId);
        }

        applyCampaignRewards(userId, txId, rp, "TOPUP");
        rewardEventRepository.saveAndFlush(new RewardEvent(txId, "TOPUP"));
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
        RewardPoints rp = getOrCreatePoints(fromUserId);
        if (points > 0) {
            rp.addPoints(points);
            pointsRepository.saveAndFlush(rp);
            log.info("Awarded {} points to sender: {}", points, fromUserId);
        }

        applyCampaignRewards(fromUserId, txId, rp, "TRANSFER");
        rewardEventRepository.saveAndFlush(new RewardEvent(txId, "TRANSFER"));
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

        applyRewardBenefit(userId, item);
        try {
            recordRewardNotification(userId, item);
        } catch (Exception exception) {
            log.error("Failed to record reward notification for user {} and item {}", userId, item.getName(), exception);
        }

        return "Successfully redeemed " + item.getName();
    }

    @Transactional
    public RewardCatalog createCatalogItem(RewardCatalog item) {
        return catalogRepository.save(item);
    }

    private void applyRewardBenefit(UUID userId, RewardCatalog item) {
        String rewardName = item.getName() != null ? item.getName().toLowerCase() : "";
        if (!rewardName.contains("cashback")) {
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId.toString());
        request.put("amount", extractCashbackAmount(item));
        request.put("rewardName", item.getName());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, internalHeaders());
        restTemplate.postForEntity(walletInternalBaseUrl + "/reward-credit", entity, String.class);
    }

    private BigDecimal extractCashbackAmount(RewardCatalog item) {
        String source = (item.getName() + " " + item.getDescription()).toLowerCase();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:rs|inr)\\s*(\\d+(?:\\.\\d+)?)").matcher(source);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }
        return new BigDecimal("100");
    }

    private void recordRewardNotification(UUID userId, RewardCatalog item) {
        if (restTemplate == null) {
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("userId", userId.toString());
        request.put("topic", "reward.redeemed");
        request.put("subject", "Reward redeemed");
        request.put("body", buildRewardMessage(item));

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, internalHeaders());
        restTemplate.postForEntity(notificationInternalBaseUrl + "/record", entity, String.class);
    }

    private String buildRewardMessage(RewardCatalog item) {
        String name = item.getName() != null ? item.getName() : "Reward";
        String lowerName = name.toLowerCase();
        if (lowerName.contains("amazon")) {
            return "You redeemed an Amazon Voucher. Your Amazon voucher is ready.";
        }
        if (lowerName.contains("flipkart")) {
            return "You redeemed a Flipkart Voucher. Your Flipkart voucher is ready.";
        }
        if (lowerName.contains("cashback")) {
            return "You redeemed " + name + ". Cashback has been credited to your wallet.";
        }
        return "You redeemed " + name + ".";
    }

    private void applyCampaignRewards(UUID userId, UUID transactionId, RewardPoints rewardPoints, String transactionType) {
        for (CampaignView campaign : getActiveCampaigns()) {
            if (!campaignAppliesToTier(campaign, rewardPoints.getTier())) {
                continue;
            }

            int bonusPoints = resolveCampaignPoints(campaign);
            if (bonusPoints <= 0 || !campaignAppliesToTransaction(campaign, transactionType)) {
                continue;
            }

            UUID campaignEventId = buildCampaignEventId(userId, transactionId, campaign);
            if (rewardEventRepository.existsById(campaignEventId)) {
                continue;
            }

            rewardPoints.addPoints(bonusPoints);
            pointsRepository.saveAndFlush(rewardPoints);
            rewardEventRepository.saveAndFlush(new RewardEvent(campaignEventId, "CAMPAIGN_" + transactionType));
            log.info("Awarded {} campaign points to user {} for campaign {}", bonusPoints, userId, campaign.getName());
        }
    }

    private UUID buildCampaignEventId(UUID userId, UUID transactionId, CampaignView campaign) {
        String trigger = campaign.getTriggerEvent() != null ? campaign.getTriggerEvent() : "FIRST_TRANSACTION";
        String eventSource = "FIRST_TRANSACTION".equalsIgnoreCase(trigger)
                ? userId + ":" + campaign.getId()
                : userId + ":" + campaign.getId() + ":" + transactionId;
        return UUID.nameUUIDFromBytes(eventSource.getBytes(StandardCharsets.UTF_8));
    }

    private List<CampaignView> getActiveCampaigns() {
        if (restTemplate == null) {
            return List.of();
        }

        try {
            ResponseEntity<CampaignView[]> response = restTemplate.exchange(
                    adminInternalBaseUrl + "/campaigns",
                    HttpMethod.GET,
                    new HttpEntity<>(internalHeaders()),
                    CampaignView[].class
            );
            if (response == null) {
                return List.of();
            }
            CampaignView[] campaigns = response.getBody();
            if (campaigns == null) {
                return List.of();
            }
            return java.util.Arrays.stream(campaigns)
                    .filter(campaign -> "ACTIVE".equalsIgnoreCase(campaign.getStatus()))
                    .toList();
        } catch (Exception exception) {
            log.warn("Unable to load active reward campaigns from admin service", exception);
            return List.of();
        }
    }

    private boolean campaignAppliesToTier(CampaignView campaign, String userTier) {
        String targetTier = campaign.getTargetTier();
        return targetTier == null
                || targetTier.isBlank()
                || "ALL".equalsIgnoreCase(targetTier)
                || targetTier.equalsIgnoreCase(userTier);
    }

    private boolean campaignAppliesToTransaction(CampaignView campaign, String transactionType) {
        String trigger = campaign.getTriggerEvent();
        if (trigger == null || trigger.isBlank()) {
            return isTransactionCampaign(campaign.getName());
        }
        if ("FIRST_TRANSACTION".equalsIgnoreCase(trigger) || "EVERY_TRANSACTION".equalsIgnoreCase(trigger)) {
            return true;
        }
        if ("TOPUP".equalsIgnoreCase(trigger) || "TOP_UP".equalsIgnoreCase(trigger)) {
            return "TOPUP".equalsIgnoreCase(transactionType);
        }
        return "TRANSFER".equalsIgnoreCase(trigger) && "TRANSFER".equalsIgnoreCase(transactionType);
    }

    private boolean isTransactionCampaign(String name) {
        String normalizedName = name != null ? name.toLowerCase() : "";
        return normalizedName.contains("transaction") || normalizedName.contains("topup") || normalizedName.contains("top-up");
    }

    private int resolveCampaignPoints(CampaignView campaign) {
        if (campaign.getRewardPoints() != null && campaign.getRewardPoints() > 0) {
            return campaign.getRewardPoints();
        }
        return extractCampaignPoints(campaign.getName());
    }

    private int extractCampaignPoints(String name) {
        Matcher matcher = REWARD_POINTS_PATTERN.matcher(name != null ? name : "");
        if (!matcher.find()) {
            return 0;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private HttpHeaders internalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);
        return headers;
    }

    public static class CampaignView {
        private UUID id;
        private String name;
        private String targetTier;
        private String status;
        private Integer rewardPoints;
        private String triggerEvent;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTargetTier() {
            return targetTier;
        }

        public void setTargetTier(String targetTier) {
            this.targetTier = targetTier;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getRewardPoints() {
            return rewardPoints;
        }

        public void setRewardPoints(Integer rewardPoints) {
            this.rewardPoints = rewardPoints;
        }

        public String getTriggerEvent() {
            return triggerEvent;
        }

        public void setTriggerEvent(String triggerEvent) {
            this.triggerEvent = triggerEvent;
        }
    }
}
