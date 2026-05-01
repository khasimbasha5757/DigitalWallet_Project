package com.wallet.auth.service;

import com.wallet.auth.dto.AuthRequest;
import com.wallet.auth.dto.AuthResponse;
import com.wallet.auth.dto.ChangePasswordRequest;
import com.wallet.auth.dto.PasswordResetOtpResponse;
import com.wallet.auth.dto.RegisterRequest;
import com.wallet.auth.entity.UserCredential;
import com.wallet.auth.repository.UserCredentialRepository;
import com.wallet.auth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wallet.auth.dto.ForgotPasswordRequest;
import com.wallet.auth.dto.ResetPasswordRequest;
import com.wallet.auth.dto.VerifyOtpRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserCredentialRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RestTemplate restTemplate;

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Gateway-Token";
    private static final String INTERNAL_SECRET_VALUE = "DigitalWalletInternalSecret2026";

    @Value("${notification.internal.email-url:http://localhost:8086/api/notifications/internal/email}")
    private String notificationInternalEmailUrl;

    public String saveUser(RegisterRequest request) {
        String normalizedEmail = normalizeValue(request.getEmail());
        String normalizedUsername = normalizeValue(request.getUsername());

        Optional<UserCredential> existing = repository.findByEmailIgnoreCase(normalizedEmail);
        if(existing.isPresent()){
            throw new RuntimeException("User already exists with email " + normalizedEmail);
        }
        if(repository.findByUsernameIgnoreCase(normalizedUsername).isPresent()){
            throw new RuntimeException("User already exists with username " + normalizedUsername);
        }
        UserCredential credential = new UserCredential();
        credential.setUsername(normalizedUsername);
        credential.setEmail(normalizedEmail);
        credential.setPassword(passwordEncoder.encode(request.getPassword()));
        credential.setRole(resolveRoleForRegistration(normalizedEmail));
        credential.setStatus("PENDING_KYC");
        credential.setFullName(normalizeValue(request.getFullName()));
        credential.setPhoneNumber(normalizeValue(request.getPhoneNumber()));
        
        // Auth service is the source of truth for identity and account status.
        repository.saveAndFlush(credential);

        return "User registration successful";
    }

    public AuthResponse login(AuthRequest request) {
        String loginId = normalizeValue(request.getUsername());
        String password = request.getPassword() == null ? "" : request.getPassword();

        UserCredential user = repository.findByUsernameIgnoreCase(loginId)
                .or(() -> repository.findByEmailIgnoreCase(loginId))
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        logger.info("Login attempt resolved for loginId={} -> userId={} email={}", loginId, user.getId(), user.getEmail());

        boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
        logger.info("Password match result for userId={} -> {}", user.getId(), passwordMatches);
        if (!passwordMatches) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // The JWT carries the core identity fields needed by downstream services.
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole(), user.getId().toString(), user.getFullName(), user.getPhoneNumber());
        logger.info("Login token generated successfully for userId={}", user.getId());
        return new AuthResponse(
                token,
                user.getId().toString(),
                user.getEmail(),
                user.getRole(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getProfileImageUrl());
    }

    public PasswordResetOtpResponse requestPasswordResetOtp(ForgotPasswordRequest request) {
        String normalizedEmail = normalizeValue(request.getEmail());
        logger.info("Received forgot-password OTP request for email={}", normalizedEmail);
        Optional<UserCredential> userOptional = repository.findByEmailIgnoreCase(normalizedEmail);
        if (userOptional.isEmpty()) {
            logger.info("Forgot-password request email not found.");
            throw new RuntimeException("Enter a registered email address.");
        }

        UserCredential user = userOptional.get();
        String otp = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        user.setPasswordResetOtp(otp);
        user.setPasswordResetOtpExpiresAt(LocalDateTime.now().plusMinutes(10));
        user.setPasswordResetOtpVerified(false);
        repository.save(user);

        try {
            sendPasswordResetOtpEmail(user, otp);
        } catch (Exception exception) {
            user.setPasswordResetOtp(null);
            user.setPasswordResetOtpExpiresAt(null);
            user.setPasswordResetOtpVerified(false);
            repository.save(user);
            logger.warn("Password reset email could not be sent for {}", user.getEmail(), exception);
            throw new RuntimeException("Unable to send OTP email. Please try again later.");
        }

        logger.info("Forgot-password OTP sent for email={}", user.getEmail());
        return new PasswordResetOtpResponse("OTP sent to your email.", true);
    }

    public boolean accountExists(String identifier) {
        String normalizedIdentifier = normalizeValue(identifier);
        if (normalizedIdentifier == null || normalizedIdentifier.isBlank()) {
            return false;
        }
        return repository.findByUsernameIgnoreCase(normalizedIdentifier).isPresent()
                || repository.findByEmailIgnoreCase(normalizedIdentifier).isPresent();
    }

    public String verifyPasswordResetOtp(VerifyOtpRequest request) {
        UserCredential user = repository.findByEmailIgnoreCase(normalizeValue(request.getEmail()))
                .orElseThrow(() -> new RuntimeException("Invalid email or OTP"));

        validateOtp(user, normalizeValue(request.getOtp()));
        user.setPasswordResetOtpVerified(true);
        repository.save(user);
        return "OTP verified successfully.";
    }

    public String resetPassword(ResetPasswordRequest request) {
        UserCredential user = repository.findByEmailIgnoreCase(normalizeValue(request.getEmail()))
                .orElseThrow(() -> new RuntimeException("Invalid password reset request"));

        validateOtp(user, normalizeValue(request.getOtp()));
        if (!user.isPasswordResetOtpVerified()) {
            throw new RuntimeException("Please verify the OTP before resetting your password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetOtp(null);
        user.setPasswordResetOtpExpiresAt(null);
        user.setPasswordResetOtpVerified(false);
        repository.save(user);
        return "Password reset successful. Please sign in with your new password.";
    }

    public void updateUserStatus(java.util.UUID userId, String status) {
        UserCredential user = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setStatus(status);
        repository.save(user);
    }

    public UserCredential getProfile(java.util.UUID userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User profile not found: " + userId));
    }

    public String getUserRole(java.util.UUID userId) {
        return repository.findById(userId)
                .map(UserCredential::getRole)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    public UserCredential updateProfile(java.util.UUID userId, Map<String, String> updates) {
        UserCredential user = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        String profileImageUrl = updates.get("profileImageUrl");
        logger.info(
                "Profile update payload for userId={} | keys={} | hasProfileImageUrl={} | profileImageLength={}",
                userId,
                updates.keySet(),
                updates.containsKey("profileImageUrl"),
                profileImageUrl == null ? 0 : profileImageUrl.length());
        
        if (updates.containsKey("fullName")) {
            user.setFullName(normalizeValue(updates.get("fullName")));
        }
        if (updates.containsKey("phoneNumber")) {
            user.setPhoneNumber(normalizeValue(updates.get("phoneNumber")));
        }
        if (updates.containsKey("email")) {
            String nextEmail = normalizeValue(updates.get("email"));
            repository.findByEmailIgnoreCase(nextEmail)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Email already exists");
                    });
            user.setEmail(nextEmail);
        }
        if (updates.containsKey("username")) {
            String nextUsername = normalizeValue(updates.get("username"));
            repository.findByUsernameIgnoreCase(nextUsername)
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Username already exists");
                    });
            user.setUsername(nextUsername);
        }
        if (updates.containsKey("profileImageUrl")) {
            user.setProfileImageUrl(updates.get("profileImageUrl"));
        }
        
        return repository.save(user);
    }

    public String changePassword(java.util.UUID userId, ChangePasswordRequest request) {
        UserCredential user = repository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect.");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new RuntimeException("New password must be different from the current password.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        repository.save(user);
        return "Password updated successfully.";
    }

    private void validateOtp(UserCredential user, String otp) {
        if (user.getPasswordResetOtp() == null || user.getPasswordResetOtpExpiresAt() == null) {
            throw new RuntimeException("No active OTP request found for this email.");
        }
        if (user.getPasswordResetOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("OTP has expired. Please request a new one.");
        }
        if (!user.getPasswordResetOtp().equals(otp)) {
            throw new RuntimeException("Invalid OTP.");
        }
    }

    private String normalizeValue(String value) {
        return value == null ? null : value.trim();
    }

    private String resolveRoleForRegistration(String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        return normalizedEmail.contains("admin") ? "ADMIN" : "USER";
    }

    private void sendPasswordResetOtpEmail(UserCredential user, String otp) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(INTERNAL_SECRET_HEADER, INTERNAL_SECRET_VALUE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("to", user.getEmail());
        payload.put("subject", "Digital Wallet Password Reset OTP");
        payload.put("body",
                "Your Digital Wallet password reset OTP is " + otp + ". It will expire in 10 minutes.");
        payload.put("topic", "auth.password.reset");

        restTemplate.postForEntity(notificationInternalEmailUrl, new HttpEntity<>(payload, headers), String.class);
    }
}
