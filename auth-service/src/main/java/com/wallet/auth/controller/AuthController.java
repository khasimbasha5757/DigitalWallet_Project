package com.wallet.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wallet.auth.dto.AuthRequest;
import com.wallet.auth.dto.AuthResponse;
import com.wallet.auth.dto.RegisterRequest;
import com.wallet.auth.service.AuthService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private AuthService service;

    @PostMapping("/signup")
    public ResponseEntity<String> addNewUser(@jakarta.validation.Valid @RequestBody RegisterRequest request) {
        try {
            return ResponseEntity.ok(service.saveUser(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> getToken(@jakarta.validation.Valid @RequestBody AuthRequest request) {
        try{
            AuthResponse response = service.login(request);
            return ResponseEntity.ok(response);
        } catch(Exception e){
            return ResponseEntity.status(401).body("Invalid Authentication");
        }
    }

    @GetMapping("/validate")
    public String validateToken(@RequestParam("token") String token) {
        // Validation happens in API gateway, but this can be a fallback endpoint
        return "Token is valid";
    }

    @PostMapping("/internal/users/{userId}/status")
    public ResponseEntity<String> updateStatus(@PathVariable java.util.UUID userId, @RequestParam String status) {
        service.updateUserStatus(userId, status);
        return ResponseEntity.ok("User status updated to " + status);
    }

    @GetMapping("/users/{userId}/profile")
    public ResponseEntity<?> getProfile(@PathVariable java.util.UUID userId, Authentication authentication) {
        java.util.UUID effectiveUserId = resolveEffectiveUserId(userId, authentication);
        logger.info("Accessing profile for requested userId: {} | effective userId: {}", userId, effectiveUserId);
        try {
            return ResponseEntity.ok(service.getProfile(effectiveUserId));
        } catch (Exception e) {
            logger.error("Error fetching profile for userId {}: {}", effectiveUserId, e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/users/{userId}/profile")
    public ResponseEntity<?> updateProfile(@PathVariable java.util.UUID userId,
            @RequestBody java.util.Map<String, String> updates,
            Authentication authentication) {
        java.util.UUID effectiveUserId = resolveEffectiveUserId(userId, authentication);
        logger.info("Updating profile for requested userId: {} | effective userId: {}", userId, effectiveUserId);
        try {
            return ResponseEntity.ok(service.updateProfile(effectiveUserId, updates));
        } catch (Exception e) {
            logger.error("Error updating profile for userId {}: {}", effectiveUserId, e.getMessage());
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    private java.util.UUID resolveEffectiveUserId(java.util.UUID requestedUserId, Authentication authentication) {
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
        if (authentication == null || authentication.getPrincipal() == null) {
            return requestedUserId;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (isAdmin) {
            return requestedUserId;
        }

        Object principal = authentication.getPrincipal();
        String principalValue = principal instanceof UserDetails userDetails
                ? userDetails.getUsername()
                : principal.toString();
        try {
            return java.util.UUID.fromString(principalValue);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid authenticated principal for profile access: {}", principalValue);
            return requestedUserId;
        }
    }
}
