package com.wallet.notification.service;

import com.wallet.common.dto.KycNotificationEvent;
import com.wallet.notification.entity.NotificationHistory;
import com.wallet.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void handleTopUp_Success() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("amount", "100.00");

        notificationService.handleTopUp(event);

        verify(notificationRepository, times(1)).save(any(NotificationHistory.class));
    }

    @Test
    void handleTransfer_Success() {
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("fromUserId", fromUserId);
        event.put("toUserId", toUserId);
        event.put("amount", "50.00");

        notificationService.handleTransfer(event);

        // Should save two notifications (one for sender, one for receiver)
        verify(notificationRepository, times(2)).save(any(NotificationHistory.class));
    }

    @Test
    void handleTopUp_Exception_Logged() {
        Map<String, Object> event = new HashMap<>();
        // Missing userId will cause exception
        event.put("amount", "100.00");

        notificationService.handleTopUp(event);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void handleKycStatusUpdate_Approved_SendsEmailAndStoresHistory() {
        KycNotificationEvent event = new KycNotificationEvent(userId, "user@example.com", "APPROVED",
                "Identity verification successful", "KYC_UPDATE");

        notificationService.handleKycStatusUpdate(event);

        verify(emailService).sendEmail("user@example.com", "KYC Status Update: APPROVED",
                "Your KYC status has been updated to: APPROVED. You can now perform full transactions.");
        verify(notificationRepository).save(any(NotificationHistory.class));
    }

    @Test
    void handleKycStatusUpdate_Rejected_UsesReasonInMessage() {
        KycNotificationEvent event = new KycNotificationEvent(userId, "user@example.com", "REJECTED",
                "Documents unclear", "KYC_UPDATE");

        notificationService.handleKycStatusUpdate(event);

        verify(emailService).sendEmail("user@example.com", "KYC Status Update: REJECTED",
                "Your KYC status has been updated to: REJECTED. Reason: Documents unclear");
        verify(notificationRepository).save(any(NotificationHistory.class));
    }

    @Test
    void handleKycStatusUpdate_EmailFailureIsRethrown() {
        KycNotificationEvent event = new KycNotificationEvent(userId, "user@example.com", "APPROVED",
                "Identity verification successful", "KYC_UPDATE");
        doThrow(new RuntimeException("Email delivery failed")).when(emailService)
                .sendEmail(any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> notificationService.handleKycStatusUpdate(event));

        verify(notificationRepository, never()).save(any(NotificationHistory.class));
    }

    @Test
    void handleTransfer_Exception_Logged() {
        Map<String, Object> event = new HashMap<>();
        event.put("fromUserId", userId);
        event.put("amount", "50.00");

        notificationService.handleTransfer(event);

        verify(notificationRepository, never()).save(any());
    }
}
