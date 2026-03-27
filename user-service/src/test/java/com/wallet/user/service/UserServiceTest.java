package com.wallet.user.service;

import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.repository.KycRepository;
import com.wallet.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private KycRepository kycRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private KycSubmitRequest kycSubmitRequest;
    private KycDetails kycDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        kycSubmitRequest = new KycSubmitRequest("PASSPORT", "ABC12345", "http://docs.com/123");
        kycDetails = new KycDetails();
        kycDetails.setUserId(userId);
        kycDetails.setStatus("PENDING");
    }

    @Test
    void submitKyc_Success() {
        when(kycRepository.findByUserId(userId)).thenReturn(Optional.empty());

        String result = userService.submitKyc(userId, "test@example.com", "USER", kycSubmitRequest);

        assertEquals("KYC details submitted successfully and pending approval", result);
        verify(kycRepository, times(1)).save(any(KycDetails.class));
    }

    @Test
    void submitKyc_AlreadyApproved() {
        kycDetails.setStatus("APPROVED");
        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(kycDetails));

        assertThrows(RuntimeException.class, () -> userService.submitKyc(userId, "test@example.com", "USER", kycSubmitRequest));
    }

    @Test
    void getKycStatus_Success() {
        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(kycDetails));

        KycDetails result = userService.getKycStatus(userId, "test@example.com", "USER");

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
    }

    @Test
    void getPendingKycs_Success() {
        when(kycRepository.findByStatus("PENDING")).thenReturn(List.of(kycDetails));

        List<KycDetails> result = userService.getPendingKycs();

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void updateKycStatus_Approved_Success() {
        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(kycDetails));

        userService.updateKycStatus(userId, "APPROVED", null);

        assertEquals("APPROVED", kycDetails.getStatus());
        verify(kycRepository, times(1)).save(kycDetails);
        verify(restTemplate, times(1)).postForEntity(contains("ACTIVE"), any(), eq(String.class));
    }

    @Test
    void updateKycStatus_Rejected_Success() {
        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(kycDetails));

        userService.updateKycStatus(userId, "REJECTED", "Documents unclear");

        assertEquals("REJECTED", kycDetails.getStatus());
        assertEquals("Documents unclear", kycDetails.getRejectionReason());
        verify(restTemplate, times(1)).postForEntity(contains("REJECTED"), any(), eq(String.class));
    }

    @Test
    void updateKycStatus_BackfillsMissingEmail_FromUserRecord() {
        com.wallet.user.entity.User user = new com.wallet.user.entity.User();
        user.setId(userId);
        user.setEmail("registered@gmail.com");
        kycDetails.setEmail(" ");

        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(kycDetails));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        KycDetails result = userService.updateKycStatus(userId, "APPROVED", null);

        assertEquals("registered@gmail.com", result.getEmail());
        verify(userRepository, times(1)).findById(userId);
    }
}
