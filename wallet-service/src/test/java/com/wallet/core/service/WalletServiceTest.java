package com.wallet.core.service;

import com.wallet.core.dto.TopUpRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.entity.WalletAccount;
import com.wallet.core.repository.WalletAccountRepository;
import com.wallet.core.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletAccountRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private WalletService walletService;

    private UUID userId;
    private WalletAccount walletAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        walletAccount = new WalletAccount();
        walletAccount.setUserId(userId);
        walletAccount.setCachedBalance(new BigDecimal("100.00"));
        walletAccount.setStatus("ACTIVE");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        lenient().when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        lenient().when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "APPROVED")));
    }

    @Test
    void getBalance_FromRedis() {
        when(valueOperations.get(anyString())).thenReturn("150.00");

        BigDecimal balance = walletService.getBalance(userId, "Bearer testToken");

        assertEquals(new BigDecimal("150.00"), balance);
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void getBalance_FromDb() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(walletAccount));

        BigDecimal balance = walletService.getBalance(userId, "Bearer testToken");

        assertEquals(new BigDecimal("100.00"), balance);
        verify(valueOperations, times(1)).set(anyString(), anyString());
    }

    @Test
    void topUp_Success() {
        TopUpRequest request = new TopUpRequest(userId, new BigDecimal("50.00"), "UPI");
        when(repository.findByUserId(userId)).thenReturn(Optional.of(walletAccount));

        String result = walletService.topUp(userId, request, "Bearer testToken");

        assertEquals("Top-up successful. Balance updated.", result);
        assertEquals(new BigDecimal("150.00"), walletAccount.getCachedBalance());
        verify(repository, times(1)).saveAndFlush(walletAccount);
        verify(kafkaTemplate, times(1)).send(eq("wallet.topup.success"), anyMap());
    }

    @Test
    void transfer_Success() {
        UUID targetUserId = UUID.randomUUID();
        WalletAccount targetAccount = new WalletAccount();
        targetAccount.setUserId(targetUserId);
        targetAccount.setCachedBalance(BigDecimal.ZERO);

        TransferRequest request = new TransferRequest(targetUserId, new BigDecimal("40.00"), "Dinner");
        
        when(repository.findByUserId(userId)).thenReturn(Optional.of(walletAccount));
        when(repository.findByUserId(targetUserId)).thenReturn(Optional.of(targetAccount));

        String result = walletService.transfer(userId, request, "Bearer testToken");

        assertEquals("Transfer successful.", result);
        assertEquals(new BigDecimal("60.00"), walletAccount.getCachedBalance());
        assertEquals(new BigDecimal("40.00"), targetAccount.getCachedBalance());
        verify(repository, times(2)).saveAndFlush(any());
        verify(kafkaTemplate, times(1)).send(eq("wallet.transfer.completed"), anyMap());
    }

    @Test
    void transfer_InsufficientFunds() {
        UUID targetUserId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(targetUserId, new BigDecimal("200.00"), "Rent");
        
        when(repository.findByUserId(userId)).thenReturn(Optional.of(walletAccount));

        assertThrows(RuntimeException.class, () -> walletService.transfer(userId, request, "Bearer testToken"));
    }

    @Test
    void transfer_ToSelf() {
        TransferRequest request = new TransferRequest(userId, new BigDecimal("10.00"), "Self");
        assertThrows(RuntimeException.class, () -> walletService.transfer(userId, request, "Bearer testToken"));
    }

    @Test
    void getBalance_KycNotSubmitted() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new RuntimeException("Complete your KYC submission before using wallet services."));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.getBalance(userId, "Bearer testToken")
        );

        assertEquals("Complete your KYC submission before using wallet services.", exception.getMessage());
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void getBalance_KycPendingApproval() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("status", "PENDING")));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.getBalance(userId, "Bearer testToken")
        );

        assertEquals("Your KYC is still pending approval. Wallet services unlock only after approval.", exception.getMessage());
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void getBalance_AdminRoleBlocked() {
        when(jwtUtil.extractRole(anyString())).thenReturn("ADMIN");

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.getBalance(userId, "Bearer adminToken")
        );

        assertEquals("Admin accounts cannot use wallet balance, top-up, or transfer services.", exception.getMessage());
        verify(repository, never()).findByUserId(any());
    }

    @Test
    void transfer_ToAdminRecipientBlocked() {
        UUID targetUserId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(targetUserId, new BigDecimal("40.00"), "Dinner");
        when(restTemplate.exchange(contains("/role"), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("role", "ADMIN")));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.transfer(userId, request, "Bearer testToken")
        );

        assertEquals("Transfers to admin accounts are not allowed.", exception.getMessage());
        verify(repository, never()).findByUserId(any());
    }
}
