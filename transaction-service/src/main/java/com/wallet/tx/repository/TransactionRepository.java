package com.wallet.tx.repository;

import com.wallet.tx.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    Page<Transaction> findByFromUserIdOrToUserIdOrderByTimestampDesc(UUID fromUserId, UUID toUserId, Pageable pageable);
    boolean existsById(UUID id);
}
