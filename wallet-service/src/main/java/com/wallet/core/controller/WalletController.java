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
        String userId = jwtUtil.extractUserId(token);
        BigDecimal balance = walletService.getBalance(UUID.fromString(userId));
        return ResponseEntity.ok(Map.of("userId", userId, "balance", balance));
    }

    @PostMapping("/topup")
    public ResponseEntity<?> initiateTopUp(@Parameter(hidden = true) @RequestHeader("Authorization") String token, 
                                           @jakarta.validation.Valid @RequestBody TopUpRequest request) {
        String userId = jwtUtil.extractUserId(token);
        try {
            return ResponseEntity.ok(walletService.topUp(UUID.fromString(userId), request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transferMoney(@Parameter(hidden = true) @RequestHeader("Authorization") String token, 
                                           @jakarta.validation.Valid @RequestBody TransferRequest request) {
        String userId = jwtUtil.extractUserId(token);
        try {
            return ResponseEntity.ok(walletService.transfer(UUID.fromString(userId), request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
