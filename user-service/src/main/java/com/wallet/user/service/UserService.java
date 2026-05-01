package com.wallet.user.service;

import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.repository.KycRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final String PDF_CONTENT_TYPE_PREFIX = "data:application/pdf";

    @Autowired
    private KycRepository kycRepository;

    @Autowired
    private com.wallet.user.repository.UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${gateway.base-url:http://localhost:8090}")
    private String gatewayBaseUrl;

    @Value("${kyc.upload-dir:uploads/kyc}")
    private String kycUploadDir;

    @Value("${user.public-base-url:http://localhost:8082}")
    private String userPublicBaseUrl;

    public Optional<com.wallet.user.entity.User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

    public KycSubmitRequest buildKycRequest(String documentType, String documentNumber, MultipartFile documentFile) {
        if (documentType == null || documentType.isBlank()) {
            throw new RuntimeException("Document type is required");
        }
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new RuntimeException("Document number is required");
        }
        if (documentFile == null || documentFile.isEmpty()) {
            throw new RuntimeException("PDF document is required");
        }

        String originalFilename = Optional.ofNullable(documentFile.getOriginalFilename()).orElse("");
        boolean isPdf = "application/pdf".equalsIgnoreCase(documentFile.getContentType())
                || originalFilename.toLowerCase().endsWith(".pdf");
        if (!isPdf) {
            throw new RuntimeException("KYC document must be submitted as a PDF file");
        }

        try {
            Path uploadRoot = Path.of(kycUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadRoot);
            String storedFilename = UUID.randomUUID() + ".pdf";
            Path targetFile = uploadRoot.resolve(storedFilename).normalize();
            if (!targetFile.startsWith(uploadRoot)) {
                throw new RuntimeException("Invalid PDF file");
            }
            documentFile.transferTo(targetFile);
            String documentUrl = userPublicBaseUrl.replaceAll("/$", "") + "/api/users/kyc/documents/" + storedFilename;
            return new KycSubmitRequest(documentType.trim(), documentNumber.trim(), documentUrl);
        } catch (IOException e) {
            throw new RuntimeException("Could not save PDF document");
        }
    }

    @Transactional
    public String submitKyc(UUID userId, String email, String role, KycSubmitRequest request) {
        System.out.println("DEBUG: SubmitKYC called for UserID: " + userId + ", Email: " + email);
        validatePdfDocument(request.getDocumentUrl());
        
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
        // A user can resubmit until approval, but an approved KYC is treated as final here.
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

        if ("APPROVED".equals(status)) {
            validatePdfDocument(kyc.getDocumentUrl());
        }

        if (kyc.getEmail() == null || kyc.getEmail().isBlank()) {
            userRepository.findById(userId).ifPresent(user -> kyc.setEmail(user.getEmail()));
        }
        
        kyc.setStatus(status);
        kyc.setRejectionReason(rejectionReason);
        kyc.setProcessedAt(java.time.LocalDateTime.now());
        kycRepository.save(kyc);
        kycRepository.flush();

        // Auth service is updated so login/profile flows reflect the latest compliance state.
        // Determine global status
        String newStatus = "PENDING_KYC";
        if ("APPROVED".equals(status)) {
            newStatus = "ACTIVE";
        } else if ("REJECTED".equals(status)) {
            newStatus = "REJECTED";
        }

        // Notify Auth Service (Single Source of Truth)
        try {
            String authServiceUrl = gatewayBaseUrl + "/api/auth/internal/users/" + userId + "/status?status="
                    + newStatus;
            restTemplate.postForEntity(authServiceUrl, null, String.class);
            System.out.println("Synchronized status " + newStatus + " with Auth Service for UserID: " + userId);
        } catch (Exception e) {
            System.err.println("Failed to sync status with Auth Service: " + e.getMessage());
        }

        return kyc; // Return the full object including email
    }

    private void validatePdfDocument(String documentUrl) {
        String normalizedUrl = documentUrl == null ? "" : documentUrl.trim().toLowerCase();
        if (normalizedUrl.isBlank()) {
            throw new RuntimeException("KYC document PDF is required");
        }

        boolean isPdf = normalizedUrl.startsWith(PDF_CONTENT_TYPE_PREFIX)
                || normalizedUrl.matches("^https?://.+\\.pdf($|[?#].*)");
        if (!isPdf) {
            throw new RuntimeException("KYC document must be submitted as a PDF file");
        }
    }
}
