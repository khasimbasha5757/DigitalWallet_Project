package com.wallet.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "notification_history")
public class NotificationHistory {

    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String topic;

    @Column(length = 1000)
    private String message;

    private LocalDateTime sentAt = LocalDateTime.now(IST_ZONE);
    
    // In real app: EMAIL, SMS, PUSH
    private String type = "EMAIL";

    public NotificationHistory() {}

    public NotificationHistory(UUID id, UUID userId, String topic, String message, LocalDateTime sentAt, String type) {
        this.id = id;
        this.userId = userId;
        this.topic = topic;
        this.message = message;
        this.sentAt = sentAt;
        this.type = type;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @PrePersist
    public void ensureSentAtInIst() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now(IST_ZONE);
        }
    }
}
