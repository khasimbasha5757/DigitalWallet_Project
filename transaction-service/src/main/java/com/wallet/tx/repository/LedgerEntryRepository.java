package com.wallet.tx.repository;

import com.wallet.tx.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    @Query("SELECT COALESCE(SUM(CASE WHEN l.type = 'CREDIT' THEN l.amount ELSE -l.amount END), 0) FROM LedgerEntry l WHERE l.accountId = :accountId")
    BigDecimal calculateBalance(@Param("accountId") UUID accountId);
}
