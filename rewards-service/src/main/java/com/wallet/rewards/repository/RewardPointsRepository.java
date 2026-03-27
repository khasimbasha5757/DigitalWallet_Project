package com.wallet.rewards.repository;

import com.wallet.rewards.entity.RewardPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface RewardPointsRepository extends JpaRepository<RewardPoints, UUID> {
    Optional<RewardPoints> findByUserId(UUID userId);

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO reward_points (id, user_id, total_points, tier, last_updated) " +
                   "VALUES (gen_random_uuid(), :userId, 0, 'SILVER', NOW()) " +
                   "ON CONFLICT (user_id) DO NOTHING", nativeQuery = true)
    void ensureUserExists(@Param("userId") UUID userId);
}
