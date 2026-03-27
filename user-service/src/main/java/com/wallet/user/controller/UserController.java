package com.wallet.user.controller;

import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.service.UserService;
import com.wallet.user.util.JwtUtil;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/kyc")
    public ResponseEntity<?> submitKyc(@Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @jakarta.validation.Valid @RequestBody KycSubmitRequest request) {
        try {

            UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
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

    // Internal endpoint for other services to get user details
    @GetMapping("/internal/{userId}")
    public ResponseEntity<?> getUserInternal(@PathVariable UUID userId) {
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
