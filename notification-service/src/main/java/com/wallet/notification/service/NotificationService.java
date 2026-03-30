package com.wallet.notification.service;

import com.wallet.common.dto.KycNotificationEvent;
import com.wallet.notification.entity.NotificationHistory;
import com.wallet.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EmailService emailService;

    @KafkaListener(topics = "wallet.topup.success", groupId = "notification-group")
    public void handleTopUp(Map<String, Object> event) {
        logger.info("Notification Service received Top-Up: {}", event);
        try {
            UUID userId = UUID.fromString(event.get("userId").toString());
            String amount = event.get("amount").toString();
            String message = "Your wallet has been topped up successfully with amount " + amount;

            logger.info("Sending Notification: UserId={}, Message={}", userId, message);

            NotificationHistory history = new NotificationHistory();
            history.setUserId(userId);
            history.setTopic("wallet.topup.success");
            history.setMessage(message);
            notificationRepository.save(history);

        } catch (Exception e) {
            logger.error("Error processing topup notification", e);
        }
    }

    @KafkaListener(topics = "kyc.status.updated", groupId = "notification-group")
    public void handleKycStatusUpdate(KycNotificationEvent event) {
        logger.info("Notification Service received KYC Update: {}", event.getStatus());
        try {
            UUID userId = event.getUserId();
            String status = event.getStatus();
            String reason = event.getReason();
            
            String message = "Your KYC status has been updated to: " + status;
            if ("REJECTED".equals(status)) {
                message += ". Reason: " + (reason != null ? reason : "Identity verification failed");
            } else if ("APPROVED".equals(status)) {
                message += ". You can now perform full transactions.";
            }

            // Use real email from Kafka event
            String userEmail = event.getUserEmail();
            
            logger.info("Triggering Real Email to={} Message={}", userEmail, message);
            
            // KYC mails are sent asynchronously after admin-service publishes the status event.
            emailService.sendEmail(userEmail, "KYC Status Update: " + status, message);

            NotificationHistory history = new NotificationHistory();
            history.setUserId(userId);
            history.setTopic("kyc.status.updated");
            history.setMessage(message);
            notificationRepository.save(history);

        } catch (Exception e) {
             logger.error("Error processing KYC notification", e);
             throw e; // Rethrow to trigger Kafka retry
        }
    }

    @KafkaListener(topics = "wallet.transfer.completed", groupId = "notification-group")
    public void handleTransfer(Map<String, Object> event) {
        logger.info("Notification Service received Transfer: {}", event);
        try {
            UUID fromUserId = UUID.fromString(event.get("fromUserId").toString());
            UUID toUserId = UUID.fromString(event.get("toUserId").toString());
            String amount = event.get("amount").toString();

            String senderMsg = "You successfully sent " + amount + " to user " + toUserId;
            String receiverMsg = "You received " + amount + " from user " + fromUserId;

            logger.info("Sending Notification: UserId={}, Message={}", fromUserId, senderMsg);
            logger.info("Sending Notification: UserId={}, Message={}", toUserId, receiverMsg);

            NotificationHistory senderHistory = new NotificationHistory();
            senderHistory.setUserId(fromUserId);
            senderHistory.setTopic("wallet.transfer.completed");
            senderHistory.setMessage(senderMsg);
            notificationRepository.save(senderHistory);

            NotificationHistory receiverHistory = new NotificationHistory();
            receiverHistory.setUserId(toUserId);
            receiverHistory.setTopic("wallet.transfer.completed");
            receiverHistory.setMessage(receiverMsg);
            notificationRepository.save(receiverHistory);

        } catch (Exception e) {
            logger.error("Error processing transfer notification", e);
        }
    }
}
