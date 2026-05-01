package com.wallet.auth.dto;

public class PasswordResetOtpResponse {
    private String message;
    private boolean emailDispatched;

    public PasswordResetOtpResponse() {}

    public PasswordResetOtpResponse(String message, boolean emailDispatched) {
        this.message = message;
        this.emailDispatched = emailDispatched;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isEmailDispatched() { return emailDispatched; }
    public void setEmailDispatched(boolean emailDispatched) { this.emailDispatched = emailDispatched; }
}
