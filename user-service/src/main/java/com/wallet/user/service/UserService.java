package com.wallet.user.service;

import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.repository.KycRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private KycRepository kycRepository;

    @Autowired
    private com.wallet.user.repository.UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    public Optional<com.wallet.user.entity.User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

    @Transactional
    public String submitKyc(UUID userId, String email, String role, KycSubmitRequest request) {
        System.out.println("DEBUG: SubmitKYC called for UserID: " + userId + ", Email: " + email);
        
        Optional<KycDetails> existingKyc = kycRepository.findByUserId(userId);
        if (existingKyc.isPresent() && existingKyc.get().getStatus().equals("APPROVED")) {
            throw new RuntimeException("KYC is already approved");
        }

        KycDetails kyc = existingKyc.orElse(new KycDetails());
        kyc.setUserId(userId);
        kyc.setEmail(email); // Save email here
        kyc.setDocumentType(request.getDocumentType());
        kyc.setDocumentNumber(request.getDocumentNumber());
        kyc.setDocumentUrl(request.getDocumentUrl());
        kyc.setStatus("PENDING");
        kycRepository.save(kyc);

        return "KYC details submitted successfully and pending approval";
    }

    public KycDetails getKycStatus(UUID userId, String email, String role) {
        return kycRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("KYC details not found"));
    }

    public List<KycDetails> getPendingKycs() {
        return kycRepository.findByStatus("PENDING");
    }

    @Transactional
    public KycDetails updateKycStatus(UUID userId, String status, String rejectionReason) {
        System.out.println("DEBUG: updateKycStatus called for UserID: " + userId + ", NewStatus: " + status + ", Reason: " + rejectionReason);
        KycDetails kyc = kycRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("KYC submission not found for ID: " + userId));

        if (kyc.getEmail() == null || kyc.getEmail().isBlank()) {
            userRepository.findById(userId).ifPresent(user -> kyc.setEmail(user.getEmail()));
        }
        
        kyc.setStatus(status);
        kyc.setRejectionReason(rejectionReason);
        kyc.setProcessedAt(java.time.LocalDateTime.now());
        kycRepository.save(kyc);
        kycRepository.flush();

        // Determine global status
        String newStatus = "PENDING_KYC";
        if ("APPROVED".equals(status)) {
            newStatus = "ACTIVE";
        } else if ("REJECTED".equals(status)) {
            newStatus = "REJECTED";
        }

        // Notify Auth Service (Single Source of Truth)
        try {
            String authServiceUrl = "http://localhost:8090/api/auth/internal/users/" + userId + "/status?status="
                    + newStatus;
            restTemplate.postForEntity(authServiceUrl, null, String.class);
            System.out.println("Synchronized status " + newStatus + " with Auth Service for UserID: " + userId);
        } catch (Exception e) {
            System.err.println("Failed to sync status with Auth Service: " + e.getMessage());
        }

        return kyc; // Return the full object including email
    }
}
