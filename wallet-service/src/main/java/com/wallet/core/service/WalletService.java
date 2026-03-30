package com.wallet.core.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

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

    @Autowired
    private RestTemplate restTemplate;

    private static final String REDIS_PREFIX = "wallet_balance_";
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Gateway-Token";
    private static final String INTERNAL_SECRET_VALUE = "DigitalWalletInternalSecret2026";
    @Value("${tx.internal.base-url:http://localhost:8084/api/transactions/internal}")
    private String txInternalBaseUrl;

    @Value("${rewards.internal.base-url:http://localhost:8085/api/rewards/internal}")
    private String rewardsInternalBaseUrl;

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
        // The wallet record is lazily created the first time a user interacts with balance APIs.
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

        // One top-up event fans out into ledger, rewards, and notification processing.
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("amount", request.getAmount());
        event.put("paymentMethod", request.getPaymentMethod());
        event.put("type", "CREDIT");
        event.put("transactionId", UUID.randomUUID());

        recordTopUpSynchronously(event);
        applyTopUpRewardsSynchronously(event);

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

        // Both wallet accounts are updated inside the same transaction boundary.
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

        recordTransferSynchronously(event);
        applyTransferRewardsSynchronously(event);

        try {
            kafkaTemplate.send("wallet.transfer.completed", event);
            System.out.println("DEBUG: Kafka event sent for transfer FROM: " + fromUserId + " TO: " + request.getTargetUserId());
        } catch (Exception e) {
            System.err.println("Kafka unavailable: " + e.getMessage());
        }
        
        return "Transfer successful.";
    }

    private void recordTopUpSynchronously(Map<String, Object> event) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(event, headers);
        restTemplate.postForEntity(txInternalBaseUrl + "/topup", entity, String.class);
    }

    private void recordTransferSynchronously(Map<String, Object> event) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(event, headers);
        restTemplate.postForEntity(txInternalBaseUrl + "/transfer", entity, String.class);
    }

    private void applyTopUpRewardsSynchronously(Map<String, Object> event) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(event, headers);
        restTemplate.postForEntity(rewardsInternalBaseUrl + "/topup", entity, String.class);
    }

    private void applyTransferRewardsSynchronously(Map<String, Object> event) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(event, headers);
        restTemplate.postForEntity(rewardsInternalBaseUrl + "/transfer", entity, String.class);
    }
}
