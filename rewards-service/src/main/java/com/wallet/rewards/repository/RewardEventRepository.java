package com.wallet.rewards.repository;

import com.wallet.rewards.entity.RewardEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RewardEventRepository extends JpaRepository<RewardEvent, UUID> {
}
