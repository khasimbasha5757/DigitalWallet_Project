package com.wallet.core.controller;

import com.wallet.core.dto.TopUpRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.service.WalletService;
import com.wallet.core.util.JwtUtil;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/balance")
    public ResponseEntity<Map<String, Object>> getBalance(@Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        // The authenticated userId comes from JWT claims, not from request parameters.
        String userId = jwtUtil.extractUserId(token);
        try {
            BigDecimal balance = walletService.getBalance(UUID.fromString(userId), token);
            return ResponseEntity.ok(Map.of("userId", userId, "balance", balance));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/topup")
    public ResponseEntity<?> initiateTopUp(@Parameter(hidden = true) @RequestHeader("Authorization") String token, 
                                           @jakarta.validation.Valid @RequestBody TopUpRequest request) {
        String userId = jwtUtil.extractUserId(token);
        try {
            return ResponseEntity.ok(walletService.topUp(UUID.fromString(userId), request, token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transferMoney(@Parameter(hidden = true) @RequestHeader("Authorization") String token, 
                                           @jakarta.validation.Valid @RequestBody TransferRequest request) {
        String userId = jwtUtil.extractUserId(token);
        try {
            return ResponseEntity.ok(walletService.transfer(UUID.fromString(userId), request, token));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/internal/reward-credit")
    public ResponseEntity<?> creditRewardCashback(@RequestBody Map<String, Object> request) {
        try {
            UUID userId = UUID.fromString(request.get("userId").toString());
            BigDecimal amount = new BigDecimal(request.get("amount").toString());
            String rewardName = request.get("rewardName") != null ? request.get("rewardName").toString() : "Reward cashback";
            return ResponseEntity.ok(walletService.creditRewardCashback(userId, amount, rewardName));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
