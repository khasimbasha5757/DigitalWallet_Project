package com.wallet.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.core.dto.TopUpRequest;
import com.wallet.core.dto.TransferRequest;
import com.wallet.core.service.WalletService;
import com.wallet.core.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WalletController.class)
@AutoConfigureMockMvc(addFilters = false)
public class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getBalance_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(walletService.getBalance(userId)).thenReturn(new BigDecimal("100.00"));

        mockMvc.perform(get("/api/wallet/balance")
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void topUp_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(userId, new BigDecimal("50.00"), "UPI");
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(walletService.topUp(eq(userId), any())).thenReturn("Top-up successful");

        mockMvc.perform(post("/api/wallet/topup")
                .header("Authorization", "Bearer testToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Top-up successful"));
    }

    @Test
    void transfer_Success() throws Exception {
        UUID fromUserId = UUID.randomUUID();
        UUID toUserId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(toUserId, new BigDecimal("40.00"), "Rent");
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(fromUserId.toString());
        when(walletService.transfer(eq(fromUserId), any())).thenReturn("Transfer successful");

        mockMvc.perform(post("/api/wallet/transfer")
                .header("Authorization", "Bearer testToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Transfer successful"));
    }

    @Test
    void transfer_InsufficientFunds() throws Exception {
        UUID userId = UUID.randomUUID();
        TransferRequest request = new TransferRequest(UUID.randomUUID(), new BigDecimal("1000.00"), "Rent");
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(walletService.transfer(any(), any())).thenThrow(new RuntimeException("Insufficient funds"));

        mockMvc.perform(post("/api/wallet/transfer")
                .header("Authorization", "Bearer testToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Insufficient funds"));
    }
}
