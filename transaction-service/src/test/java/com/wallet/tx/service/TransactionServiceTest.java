package com.wallet.tx.service;

import com.wallet.tx.entity.LedgerEntry;
import com.wallet.tx.entity.Transaction;
import com.wallet.tx.repository.LedgerEntryRepository;
import com.wallet.tx.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private TransactionService transactionService;

    private UUID userId;
    private UUID txId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        txId = UUID.randomUUID();
    }

    @Test
    void handleTopUp_Success() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", userId);
        event.put("amount", new BigDecimal("100.00"));
        event.put("transactionId", txId);
        event.put("paymentMethod", "UPI");

        transactionService.handleTopUp(event);

        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
        verify(ledgerEntryRepository, times(1)).saveAndFlush(any(LedgerEntry.class));
    }

    @Test
    void handleTransfer_Success() {
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("fromUserId", fromUserId);
        event.put("toUserId", toUserId);
        event.put("amount", new BigDecimal("50.00"));
        event.put("transactionId", txId);
        event.put("notes", "Lunch");

        transactionService.handleTransfer(event);

        verify(transactionRepository, times(1)).saveAndFlush(any(Transaction.class));
        verify(ledgerEntryRepository, times(2)).saveAndFlush(any(LedgerEntry.class));
    }

    @Test
    void handleTopUp_Exception_Logged() {
        Map<String, Object> event = new HashMap<>();
        // Missing transactionId will cause NPE or similar
        event.put("userId", userId);

        transactionService.handleTopUp(event);

        verify(transactionRepository, never()).saveAndFlush(any());
        verify(ledgerEntryRepository, never()).saveAndFlush(any());
    }
}
