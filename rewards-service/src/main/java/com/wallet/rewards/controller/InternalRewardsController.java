package com.wallet.rewards.controller;

import com.wallet.rewards.service.RewardsService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/rewards/internal")
public class InternalRewardsController {

    @Autowired
    private RewardsService rewardsService;

    @PostMapping("/topup")
    public ResponseEntity<String> applyTopUpRewards(@RequestBody Map<String, Object> event) {
        rewardsService.applyTopUpRewards(event);
        return ResponseEntity.ok("Top-up rewards applied");
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> applyTransferRewards(@RequestBody Map<String, Object> event) {
        rewardsService.applyTransferRewards(event);
        return ResponseEntity.ok("Transfer rewards applied");
    }
}
