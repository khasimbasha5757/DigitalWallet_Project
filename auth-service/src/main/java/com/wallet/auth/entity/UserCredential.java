package com.wallet.auth.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = true)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // "USER" or "ADMIN"

    @Column(nullable = false)
    private String status; // "PENDING_KYC", "ACTIVE", "INACTIVE"

    @Column(nullable = true)
    private String fullName;

    @Column(nullable = true)
    private String phoneNumber;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(nullable = false)
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    @Column(nullable = true, length = 10)
    private String passwordResetOtp;

    @Column(nullable = true)
    private java.time.LocalDateTime passwordResetOtpExpiresAt;

    @Column(nullable = false)
    private boolean passwordResetOtpVerified = false;

    public UserCredential() {}

    public UserCredential(UUID id, String username, String email, String password, String role, String status, String fullName, String phoneNumber) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.status = status;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getPasswordResetOtp() { return passwordResetOtp; }
    public void setPasswordResetOtp(String passwordResetOtp) { this.passwordResetOtp = passwordResetOtp; }
    public java.time.LocalDateTime getPasswordResetOtpExpiresAt() { return passwordResetOtpExpiresAt; }
    public void setPasswordResetOtpExpiresAt(java.time.LocalDateTime passwordResetOtpExpiresAt) { this.passwordResetOtpExpiresAt = passwordResetOtpExpiresAt; }
    public boolean isPasswordResetOtpVerified() { return passwordResetOtpVerified; }
    public void setPasswordResetOtpVerified(boolean passwordResetOtpVerified) { this.passwordResetOtpVerified = passwordResetOtpVerified; }
}
