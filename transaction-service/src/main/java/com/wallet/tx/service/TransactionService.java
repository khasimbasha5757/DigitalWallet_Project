package com.wallet.tx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.tx.entity.LedgerEntry;
import com.wallet.tx.entity.Transaction;
import com.wallet.tx.repository.LedgerEntryRepository;
import com.wallet.tx.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @KafkaListener(topics = "wallet.topup.success", groupId = "transaction-group")
    @Transactional
    public void handleTopUp(Map<String, Object> event) {
        log.info("Received Top-Up Event: {}", event);
        try {
            recordTopUp(event);
        } catch (Exception e) {
            log.error("Error processing top-up event: {}", event, e);
        }
    }

    @KafkaListener(topics = "wallet.transfer.completed", groupId = "transaction-group")
    @Transactional
    public void handleTransfer(Map<String, Object> event) {
        log.info("Received Transfer Event: {}", event);
        try {
            recordTransfer(event);
        } catch (Exception e) {
            log.error("Error processing transfer event: {}", event, e);
        }
    }

    @Transactional
    public void recordTopUp(Map<String, Object> event) {
        UUID userId = UUID.fromString(event.get("userId").toString());
        BigDecimal amount = new BigDecimal(event.get("amount").toString());
        UUID txId = UUID.fromString(event.get("transactionId").toString());
        String method = event.get("paymentMethod") != null ? event.get("paymentMethod").toString() : "unknown";

        if (transactionRepository.existsById(txId)) {
            log.info("Skipping duplicate top-up transaction {}", txId);
            return;
        }

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setToUserId(userId);
        tx.setAmount(amount);
        tx.setType("TOPUP");
        tx.setStatus("COMPLETED");
        tx.setReferenceNotes("Topup via " + method);
        transactionRepository.saveAndFlush(tx);

        LedgerEntry credit = new LedgerEntry();
        credit.setAccountId(userId);
        credit.setTransactionId(txId);
        credit.setType("CREDIT");
        credit.setAmount(amount);
        credit.setDescription("Wallet Top-up");
        ledgerEntryRepository.saveAndFlush(credit);

        log.info("Successfully processed top-up for user: {}", userId);
    }

    @Transactional
    public void recordTransfer(Map<String, Object> event) {
        UUID fromUserId = UUID.fromString(event.get("fromUserId").toString());
        UUID toUserId = UUID.fromString(event.get("toUserId").toString());
        BigDecimal amount = new BigDecimal(event.get("amount").toString());
        UUID txId = UUID.fromString(event.get("transactionId").toString());
        String notes = event.get("notes") != null ? event.get("notes").toString() : "";

        if (transactionRepository.existsById(txId)) {
            log.info("Skipping duplicate transfer transaction {}", txId);
            return;
        }

        Transaction tx = new Transaction();
        tx.setId(txId);
        tx.setFromUserId(fromUserId);
        tx.setToUserId(toUserId);
        tx.setAmount(amount);
        tx.setType("TRANSFER");
        tx.setStatus("COMPLETED");
        tx.setReferenceNotes(notes);
        transactionRepository.saveAndFlush(tx);

        LedgerEntry debit = new LedgerEntry();
        debit.setAccountId(fromUserId);
        debit.setTransactionId(txId);
        debit.setType("DEBIT");
        debit.setAmount(amount);
        debit.setDescription("Transfer to " + toUserId);
        ledgerEntryRepository.saveAndFlush(debit);

        LedgerEntry credit = new LedgerEntry();
        credit.setAccountId(toUserId);
        credit.setTransactionId(txId);
        credit.setType("CREDIT");
        credit.setAmount(amount);
        credit.setDescription("Transfer from " + fromUserId);
        ledgerEntryRepository.saveAndFlush(credit);

        log.info("Successfully processed transfer from {} to {}", fromUserId, toUserId);
    }
}
