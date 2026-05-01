package com.wallet.user.controller;

import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.service.UserService;
import com.wallet.user.util.JwtUtil;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Value("${kyc.upload-dir:uploads/kyc}")
    private String kycUploadDir;

    @PostMapping(value = "/kyc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitKyc(@Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestParam String documentType,
            @RequestParam String documentNumber,
            @RequestParam("documentFile") MultipartFile documentFile) {
        try {
            // User identity is derived from JWT rather than trusting client-supplied IDs.
            UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            KycSubmitRequest request = userService.buildKycRequest(documentType, documentNumber, documentFile);
            return ResponseEntity.ok(userService.submitKyc(userId, email, role, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("System Error: " + e.getMessage());
        }
    }

    @GetMapping("/kyc/status")
    public ResponseEntity<KycDetails> getKycStatus(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {

        UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);
        return ResponseEntity.ok(userService.getKycStatus(userId, email, role));
    }

    @GetMapping(value = "/kyc/documents/{fileName}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<Resource> getKycDocument(@PathVariable String fileName) {
        try {
            Path uploadRoot = Path.of(kycUploadDir).toAbsolutePath().normalize();
            Path documentPath = uploadRoot.resolve(fileName).normalize();
            if (!documentPath.startsWith(uploadRoot) || !fileName.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(documentPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Internal endpoint for other services to get user details
    @Hidden
    @GetMapping("/internal/{userId}")
    public ResponseEntity<?> getUserInternal(@PathVariable UUID userId) {
        // Internal admin flows use this lightweight lookup to resolve user metadata such as email.
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
