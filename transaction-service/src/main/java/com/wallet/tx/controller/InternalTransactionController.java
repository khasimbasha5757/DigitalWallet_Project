package com.wallet.tx.controller;

import com.wallet.tx.service.TransactionService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/transactions/internal")
public class InternalTransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping("/topup")
    public ResponseEntity<String> recordTopUp(@RequestBody Map<String, Object> event) {
        transactionService.recordTopUp(event);
        return ResponseEntity.ok("Top-up transaction recorded");
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> recordTransfer(@RequestBody Map<String, Object> event) {
        transactionService.recordTransfer(event);
        return ResponseEntity.ok("Transfer transaction recorded");
    }
}
