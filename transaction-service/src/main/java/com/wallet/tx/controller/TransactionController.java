package com.wallet.tx.controller;

import com.wallet.tx.entity.Transaction;
import com.wallet.tx.repository.LedgerEntryRepository;
import com.wallet.tx.repository.TransactionRepository;
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

    @GetMapping("/history/{userId}")
    public ResponseEntity<Page<Transaction>> getHistory(@PathVariable UUID userId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        Page<Transaction> history = transactionRepository.findByFromUserIdOrToUserIdOrderByTimestampDesc(userId, userId, PageRequest.of(page, size));
        return ResponseEntity.ok(history);
    }

    @GetMapping("/ledger-balance/{userId}")
    public ResponseEntity<Map<String, Object>> getTrueLedgerBalance(@PathVariable UUID userId) {
        BigDecimal balance = ledgerEntryRepository.calculateBalance(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "ledgerBalance", balance));
    }
}
