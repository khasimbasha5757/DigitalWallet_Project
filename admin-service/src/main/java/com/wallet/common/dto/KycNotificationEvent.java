package com.wallet.common.dto;

import java.util.UUID;

public class KycNotificationEvent {
    private UUID userId;
    private String userEmail;
    private String status;
    private String reason;
    private String type;

    public KycNotificationEvent() {}

    public KycNotificationEvent(UUID userId, String userEmail, String status, String reason, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.status = status;
        this.reason = reason;
        this.type = type;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
