package com.wallet.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.user.dto.KycSubmitRequest;
import com.wallet.user.entity.KycDetails;
import com.wallet.user.service.UserService;
import com.wallet.user.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitKyc_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        KycSubmitRequest request = new KycSubmitRequest("PASSPORT", "ABC12345", "http://docs.com/1");
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        when(userService.submitKyc(any(), any(), any(), any())).thenReturn("KYC details submitted successfully");

        mockMvc.perform(post("/api/users/kyc")
                .header("Authorization", "Bearer testToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("KYC details submitted successfully"));
    }

    @Test
    void getKycStatus_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        KycDetails kyc = new KycDetails();
        kyc.setUserId(userId);
        kyc.setStatus("PENDING");

        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(userService.getKycStatus(any(), any(), any())).thenReturn(kyc);

        mockMvc.perform(get("/api/users/kyc/status")
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitKyc_ValidationError() throws Exception {
        KycSubmitRequest request = new KycSubmitRequest("PASSPORT", "ABC12345", "http://docs.com/1");
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(UUID.randomUUID().toString());
        when(userService.submitKyc(any(), any(), any(), any())).thenThrow(new RuntimeException("KYC is already approved"));

        mockMvc.perform(post("/api/users/kyc")
                .header("Authorization", "Bearer testToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Validation Error: KYC is already approved"));
    }
}
