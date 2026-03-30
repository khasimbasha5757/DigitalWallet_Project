package com.wallet.tx.controller;

import com.wallet.tx.entity.Transaction;
import com.wallet.tx.repository.LedgerEntryRepository;
import com.wallet.tx.repository.TransactionRepository;
import com.wallet.tx.util.JwtUtil;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/history")
    public ResponseEntity<Page<Transaction>> getMyHistory(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
        Page<Transaction> history = transactionRepository.findByFromUserIdOrToUserIdOrderByTimestampDesc(
                userId, userId, PageRequest.of(page, size));
        return ResponseEntity.ok(history);
    }

    @GetMapping("/ledger-balance")
    public ResponseEntity<Map<String, Object>> getMyTrueLedgerBalance(
            @Parameter(hidden = true) @RequestHeader("Authorization") String token) {
        UUID userId = UUID.fromString(jwtUtil.extractUserId(token));
        BigDecimal balance = ledgerEntryRepository.calculateBalance(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "ledgerBalance", balance));
    }

    @Hidden
    @GetMapping("/history/{userId}")
    public ResponseEntity<Page<Transaction>> getHistory(@PathVariable UUID userId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        Page<Transaction> history = transactionRepository.findByFromUserIdOrToUserIdOrderByTimestampDesc(userId, userId, PageRequest.of(page, size));
        return ResponseEntity.ok(history);
    }

    @Hidden
    @GetMapping("/ledger-balance/{userId}")
    public ResponseEntity<Map<String, Object>> getTrueLedgerBalance(@PathVariable UUID userId) {
        // This balance is computed from immutable ledger entries rather than the wallet cache.
        BigDecimal balance = ledgerEntryRepository.calculateBalance(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "ledgerBalance", balance));
    }
}
