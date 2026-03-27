package com.wallet.user.controller;

import com.wallet.user.entity.KycDetails;
import com.wallet.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal")
public class InternalKycController {

    @Autowired
    private UserService userService;

    @PostMapping("/kyc/{userId}/approve")
    public ResponseEntity<Map<String, Object>> approveKyc(@PathVariable UUID userId) {
        KycDetails kycDetails = userService.updateKycStatus(userId, "APPROVED", null);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Internal Approval Success");
        response.put("userId", kycDetails.getUserId());
        response.put("status", kycDetails.getStatus());
        response.put("email", kycDetails.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/kyc/{userId}/reject")
    public ResponseEntity<Map<String, Object>> rejectKyc(@PathVariable UUID userId, @RequestParam(required = false) String reason) {
        KycDetails kycDetails = userService.updateKycStatus(userId, "REJECTED", reason);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Internal Rejection Success");
        response.put("userId", kycDetails.getUserId());
        response.put("status", kycDetails.getStatus());
        response.put("email", kycDetails.getEmail());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/kyc/pending")
    public ResponseEntity<List<KycDetails>> getPendingKycs() {
        return ResponseEntity.ok(userService.getPendingKycs());
    }
}
