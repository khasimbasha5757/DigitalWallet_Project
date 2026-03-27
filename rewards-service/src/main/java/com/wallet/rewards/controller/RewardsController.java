package com.wallet.rewards.controller;

import com.wallet.rewards.entity.RewardCatalog;
import com.wallet.rewards.entity.RewardPoints;
import com.wallet.rewards.service.RewardsService;
import com.wallet.rewards.util.JwtUtil;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rewards")
public class RewardsController {

    @Autowired
    private RewardsService rewardsService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/summary")
    public ResponseEntity<RewardPoints> getSummary(@Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        String userId = jwtUtil.extractUserId(token);
        return ResponseEntity.ok(rewardsService.getSummary(UUID.fromString(userId)));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<RewardCatalog>> getCatalog() {
        return ResponseEntity.ok(rewardsService.getCatalog());
    }

    @PostMapping("/redeem/{catalogId}")
    public ResponseEntity<?> redeem(@Parameter(hidden = true) @RequestHeader("Authorization") String token, @PathVariable UUID catalogId) {
        String userId = jwtUtil.extractUserId(token);
        try {
            return ResponseEntity.ok(rewardsService.redeemItem(UUID.fromString(userId), catalogId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/catalog")
    public ResponseEntity<RewardCatalog> createCatalogItem(@jakarta.validation.Valid @RequestBody RewardCatalog item) {
        return ResponseEntity.ok(rewardsService.createCatalogItem(item));
    }
}
