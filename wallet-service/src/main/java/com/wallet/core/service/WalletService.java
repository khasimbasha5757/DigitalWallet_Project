package com.wallet.core.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wallet.core.dto.TopUpRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.entity.WalletAccount;
import com.wallet.core.repository.WalletAccountRepository;

@Service
public class WalletService {

    @Autowired
    private WalletAccountRepository repository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String REDIS_PREFIX = "wallet_balance_";

    public WalletAccount initializeWallet(UUID userId) {
        WalletAccount account = new WalletAccount();
        account.setUserId(userId);
        account.setCachedBalance(BigDecimal.ZERO);
        account.setStatus("ACTIVE");
        return repository.save(account);
    }

    public BigDecimal getBalance(UUID userId) {
        Object cached = redisTemplate.opsForValue().get(REDIS_PREFIX + userId);
        if (cached != null) {
            return new BigDecimal(cached.toString());
        }
        WalletAccount account = repository.findByUserId(userId).orElseGet(() -> initializeWallet(userId));
        redisTemplate.opsForValue().set(REDIS_PREFIX + userId, account.getCachedBalance().toString());
        return account.getCachedBalance();
    }

    @Transactional
    public String topUp(UUID userId, TopUpRequest request) {
        WalletAccount account = repository.findByUserId(userId).orElseGet(() -> initializeWallet(userId));
        account.setCachedBalance(account.getCachedBalance().add(request.getAmount()));
        repository.saveAndFlush(account);
        redisTemplate.opsForValue().set(REDIS_PREFIX + userId, account.getCachedBalance().toString());

        // Publish event for ledger service (Async/Resilient)
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("amount", request.getAmount());
        event.put("paymentMethod", request.getPaymentMethod());
        event.put("type", "CREDIT");
        event.put("transactionId", UUID.randomUUID());

        try {
            kafkaTemplate.send("wallet.topup.success", event);
            System.out.println("DEBUG: Kafka event sent for TOPUP User: " + userId + " Amount: " + request.getAmount());
        } catch (Exception e) {
            System.err.println("Kafka unavailable: " + e.getMessage());
        }
        
        return "Top-up successful. Balance updated.";
    }

    @Transactional
    public String transfer(UUID fromUserId, TransferRequest request) {
        if (fromUserId.equals(request.getTargetUserId())) {
            throw new RuntimeException("Cannot transfer money to yourself. Please use a different target user ID.");
        }
        WalletAccount fromAccount = repository.findByUserId(fromUserId)
                .orElseGet(() -> initializeWallet(fromUserId));
        
        BigDecimal amount = request.getAmount();
        if (fromAccount.getCachedBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        WalletAccount toAccount = repository.findByUserId(request.getTargetUserId())
                .orElseGet(() -> initializeWallet(request.getTargetUserId()));

        // Deduct and add
        fromAccount.setCachedBalance(fromAccount.getCachedBalance().subtract(amount));
        toAccount.setCachedBalance(toAccount.getCachedBalance().add(amount));

        repository.saveAndFlush(fromAccount);
        repository.saveAndFlush(toAccount);

        redisTemplate.opsForValue().set(REDIS_PREFIX + fromUserId, fromAccount.getCachedBalance().toString());
        redisTemplate.opsForValue().set(REDIS_PREFIX + request.getTargetUserId(), toAccount.getCachedBalance().toString());

        Map<String, Object> event = new HashMap<>();
        event.put("fromUserId", fromUserId);
        event.put("toUserId", request.getTargetUserId());
        event.put("amount", amount);
        event.put("notes", request.getNotes());
        event.put("type", "TRANSFER");
        event.put("transactionId", UUID.randomUUID());

        try {
            kafkaTemplate.send("wallet.transfer.completed", event);
            System.out.println("DEBUG: Kafka event sent for transfer FROM: " + fromUserId + " TO: " + request.getTargetUserId());
        } catch (Exception e) {
            System.err.println("Kafka unavailable: " + e.getMessage());
        }
        
        return "Transfer successful.";
    }
}
