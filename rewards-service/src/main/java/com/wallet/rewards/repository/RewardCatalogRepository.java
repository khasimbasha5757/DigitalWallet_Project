package com.wallet.rewards.repository;

import com.wallet.rewards.entity.RewardCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface RewardCatalogRepository extends JpaRepository<RewardCatalog, UUID> {
}
