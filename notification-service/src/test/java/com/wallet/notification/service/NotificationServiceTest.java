package com.wallet.notification.service;

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
}
