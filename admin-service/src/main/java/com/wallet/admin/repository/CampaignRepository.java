package com.wallet.admin.repository;

import com.wallet.admin.entity.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
}
