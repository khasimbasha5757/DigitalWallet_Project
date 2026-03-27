package com.wallet.rewards.service;

import com.wallet.rewards.entity.RewardCatalog;
import com.wallet.rewards.entity.RewardPoints;
import com.wallet.rewards.repository.RewardCatalogRepository;
import com.wallet.rewards.repository.RewardPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RewardsServiceTest {

    @Mock
    private RewardPointsRepository pointsRepository;

    @Mock
    private RewardCatalogRepository catalogRepository;

    @InjectMocks
    private RewardsService rewardsService;

    private UUID userId;
    private RewardPoints rewardPoints;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        rewardPoints = new RewardPoints();
        rewardPoints.setUserId(userId);
        rewardPoints.setTotalPoints(100);
        rewardPoints.setTier("BRONZE");
    }

    @Test
    void handleTopUp_AwardPoints() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("amount", new BigDecimal("500.00")); // Should award 5 points

        doNothing().when(pointsRepository).ensureUserExists(userId);
        when(pointsRepository.findByUserId(userId)).thenReturn(Optional.of(rewardPoints));

        rewardsService.handleTopUp(event);

        assertEquals(105, rewardPoints.getTotalPoints());
        verify(pointsRepository, times(1)).ensureUserExists(userId);
        verify(pointsRepository, times(1)).saveAndFlush(rewardPoints);
    }

    @Test
    void handleTransfer_AwardPointsToSender() {
        Map<String, Object> event = new HashMap<>();
        event.put("fromUserId", userId);
        event.put("amount", new BigDecimal("1000.00")); // Should award 10 points

        doNothing().when(pointsRepository).ensureUserExists(userId);
        when(pointsRepository.findByUserId(userId)).thenReturn(Optional.of(rewardPoints));

        rewardsService.handleTransfer(event);

        assertEquals(110, rewardPoints.getTotalPoints());
        verify(pointsRepository, times(1)).ensureUserExists(userId);
        verify(pointsRepository, times(1)).saveAndFlush(rewardPoints);
    }

    @Test
    void redeemItem_Success() {
        UUID catalogId = UUID.randomUUID();
        RewardCatalog item = new RewardCatalog();
        item.setId(catalogId);
        item.setName("Gift Card");
        item.setCostInPoints(50);
        item.setStockQuantity(10);
        item.setRequiredTier("ALL");

        doNothing().when(pointsRepository).ensureUserExists(userId);
        when(pointsRepository.findByUserId(userId)).thenReturn(Optional.of(rewardPoints));
        when(catalogRepository.findById(catalogId)).thenReturn(Optional.of(item));

        String result = rewardsService.redeemItem(userId, catalogId);

        assertEquals("Successfully redeemed Gift Card", result);
        assertEquals(50, rewardPoints.getTotalPoints());
        assertEquals(9, item.getStockQuantity());
        verify(pointsRepository, times(1)).save(rewardPoints);
        verify(catalogRepository, times(1)).save(item);
    }

    @Test
    void redeemItem_InsufficientPoints() {
        UUID catalogId = UUID.randomUUID();
        RewardCatalog item = new RewardCatalog();
        item.setId(catalogId);
        item.setCostInPoints(200);
        item.setRequiredTier("ALL");

        doNothing().when(pointsRepository).ensureUserExists(userId);
        when(pointsRepository.findByUserId(userId)).thenReturn(Optional.of(rewardPoints));
        when(catalogRepository.findById(catalogId)).thenReturn(Optional.of(item));

        assertThrows(RuntimeException.class, () -> rewardsService.redeemItem(userId, catalogId));
    }
}
