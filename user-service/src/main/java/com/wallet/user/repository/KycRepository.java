package com.wallet.user.repository;

import com.wallet.user.entity.KycDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycRepository extends JpaRepository<KycDetails, UUID> {
    Optional<KycDetails> findByUserId(UUID userId);
    List<KycDetails> findByStatus(String status);
}
