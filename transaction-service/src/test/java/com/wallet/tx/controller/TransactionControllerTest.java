package com.wallet.tx.controller;

import com.wallet.tx.entity.Transaction;
import com.wallet.tx.repository.LedgerEntryRepository;
import com.wallet.tx.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionRepository transactionRepository;

    @MockBean
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void getHistory_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setToUserId(userId);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setType("TOPUP");

        Page<Transaction> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 20), 1);
        when(transactionRepository.findByFromUserIdOrToUserIdOrderByTimestampDesc(eq(userId), eq(userId), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/transactions/history/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(100.00))
                .andExpect(jsonPath("$.content[0].type").value("TOPUP"));
    }

    @Test
    void getTrueLedgerBalance_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        when(ledgerEntryRepository.calculateBalance(userId)).thenReturn(new BigDecimal("150.00"));

        mockMvc.perform(get("/api/transactions/ledger-balance/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalance").value(150.00))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }
}
