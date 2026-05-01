package com.wallet.notification.controller;

import java.util.UUID;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.notification.dto.InternalEmailRequest;
import com.wallet.notification.entity.NotificationHistory;
import com.wallet.notification.repository.NotificationRepository;
import com.wallet.notification.service.EmailService;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/api/notifications/internal")
public class InternalNotificationController {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationRepository notificationRepository;

    @PostMapping("/email")
    public ResponseEntity<String> sendInternalEmail(@RequestBody InternalEmailRequest request) {
        emailService.sendEmail(request.getTo(), request.getSubject(), request.getBody());

        NotificationHistory history = new NotificationHistory();
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            history.setUserId(UUID.fromString(request.getUserId()));
        }
        history.setTopic(request.getTopic() != null ? request.getTopic() : "internal.email");
        history.setMessage(request.getBody());
        history.setType("EMAIL");
        notificationRepository.save(history);

        return ResponseEntity.ok("Email sent");
    }

    @PostMapping("/record")
    public ResponseEntity<String> recordInternalNotification(@RequestBody InternalEmailRequest request) {
        NotificationHistory history = new NotificationHistory();
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            history.setUserId(UUID.fromString(request.getUserId()));
        }
        history.setTopic(request.getTopic() != null ? request.getTopic() : "reward.redeemed");
        history.setMessage(request.getBody());
        history.setType("REWARD");
        history.setSentAt(LocalDateTime.now(IST_ZONE));
        notificationRepository.save(history);

        return ResponseEntity.ok("Notification recorded");
    }
}
