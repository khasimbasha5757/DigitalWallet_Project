package com.wallet.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wallet.common.dto.KycNotificationEvent;
import com.wallet.admin.entity.Campaign;
import com.wallet.admin.repository.CampaignRepository;

import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${gateway.base-url:http://localhost:8090}")
    private String gatewayBaseUrl;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardMetrics() {
        // Simplified metrics
        return ResponseEntity.ok(Map.of(
                "activeCampaigns", campaignRepository.count(),
                "status", "Operational"));
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Campaign> createCampaign(@jakarta.validation.Valid @RequestBody Campaign campaign) {
        return ResponseEntity.ok(campaignRepository.save(campaign));
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<Campaign>> getCampaigns() {
        return ResponseEntity.ok(campaignRepository.findAll());
    }

    @PostMapping("/kyc/{userId}/approve")
    public ResponseEntity<?> approveKyc(@PathVariable UUID userId,
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        try {
            // Admin-service orchestrates approval, but the KYC record itself lives in user-service.
            String userServiceUrl = gatewayBaseUrl + "/api/users/internal/kyc/" + userId + "/approve";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String userEmail = null;
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(userServiceUrl, entity, java.util.Map.class);
            if (response.getBody() != null) {
                userEmail = (String) response.getBody().get("email");
            }
            
            if (!isValidEmail(userEmail)) {
                userEmail = getUserEmail(userId, token);
            }

            if (!isValidEmail(userEmail)) {
                return ResponseEntity.badRequest().body("Unable to resolve a valid email address for user " + userId);
            }

            KycNotificationEvent event = new KycNotificationEvent(
                userId, 
                userEmail,
                "APPROVED", 
                "Identity verification successful", 
                "KYC_UPDATE"
            );
            try {
                // Email delivery is delegated asynchronously through Kafka to notification-service.
                kafkaTemplate.send("kyc.status.updated", event);
            } catch (Exception ex) {
                logger.error("KYC approved for user {} but notification event publish failed", userId, ex);
                return ResponseEntity.ok("KYC Approved for user " + userId + " (notification event publish failed)");
            }

            return ResponseEntity.ok("KYC Approved for user " + userId);
        } catch (Exception e) {
            logger.error("Failed to approve KYC for user {}", userId, e);
            return ResponseEntity.badRequest().body("Failed to contact User Service: " + e.getMessage());
        }
    }

    @PostMapping("/kyc/{userId}/reject")
    public ResponseEntity<?> rejectKyc(@PathVariable UUID userId,
            @RequestParam(required = false) String reason,
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        try {
            java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                    .fromHttpUrl(gatewayBaseUrl + "/api/users/internal/kyc/" + userId + "/reject")
                    .queryParam("reason", reason)
                    .build().toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String userEmail = null;
            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(uri, entity, java.util.Map.class);
            if (response.getBody() != null) {
                userEmail = (String) response.getBody().get("email");
            }
            
            if (!isValidEmail(userEmail)) {
                userEmail = getUserEmail(userId, token);
            }

            if (!isValidEmail(userEmail)) {
                return ResponseEntity.badRequest().body("Unable to resolve a valid email address for user " + userId);
            }

            KycNotificationEvent event = new KycNotificationEvent(
                userId, 
                userEmail,
                "REJECTED", 
                reason != null ? reason : "Identity verification failed", 
                "KYC_UPDATE"
            );
            try {
                // Rejection follows the same event path so notification behavior stays uniform.
                kafkaTemplate.send("kyc.status.updated", event);
            } catch (Exception ex) {
                logger.error("KYC rejected for user {} but notification event publish failed", userId, ex);
                return ResponseEntity.ok("KYC Rejected for user " + userId + " (notification event publish failed)");
            }

            return ResponseEntity.ok("KYC Rejected for user " + userId);
        } catch (Exception e) {
            logger.error("Failed to reject KYC for user {}", userId, e);
            return ResponseEntity.badRequest().body("Failed to contact User Service: " + e.getMessage());
        }
    }

    @GetMapping("/kyc/pending")
    public ResponseEntity<?> getPendingKycs(@Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        try {
            String userServiceUrl = gatewayBaseUrl + "/api/users/internal/kyc/pending";
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<List> response = restTemplate.exchange(userServiceUrl,
                    org.springframework.http.HttpMethod.GET, entity, List.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to contact User Service: " + e.getMessage());
        }
    }

    private String getUserEmail(UUID userId, String token) {
        try {
            String url = gatewayBaseUrl + "/api/users/internal/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<java.util.Map> userRes = restTemplate.exchange(url, HttpMethod.GET, entity, java.util.Map.class);
            if (userRes.getStatusCode().is2xxSuccessful() && userRes.getBody() != null) {
                return (String) userRes.getBody().get("email");
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch user email: " + e.getMessage());
        }
        return null;
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }
}
