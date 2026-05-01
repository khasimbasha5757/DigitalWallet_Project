package com.wallet.user.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Test
    void submitKyc_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        KycSubmitRequest request = new KycSubmitRequest("PASSPORT", "ABC12345", "http://localhost:8082/api/users/kyc/documents/test.pdf");
        MockMultipartFile documentFile = new MockMultipartFile("documentFile", "test.pdf", "application/pdf", "pdf".getBytes());
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(userId.toString());
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        when(userService.buildKycRequest(anyString(), anyString(), any())).thenReturn(request);
        when(userService.submitKyc(any(), any(), any(), any())).thenReturn("KYC details submitted successfully");

        mockMvc.perform(multipart("/api/users/kyc")
                .file(documentFile)
                .header("Authorization", "Bearer testToken")
                .param("documentType", "PASSPORT")
                .param("documentNumber", "ABC12345"))
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
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        when(userService.getKycStatus(any(), any(), any())).thenReturn(kyc);

        mockMvc.perform(get("/api/users/kyc/status")
                .header("Authorization", "Bearer testToken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void submitKyc_ValidationError() throws Exception {
        MockMultipartFile documentFile = new MockMultipartFile("documentFile", "test.pdf", "application/pdf", "pdf".getBytes());
        
        when(jwtUtil.extractUserId(anyString())).thenReturn(UUID.randomUUID().toString());
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        when(userService.buildKycRequest(anyString(), anyString(), any())).thenThrow(new RuntimeException("KYC is already approved"));

        mockMvc.perform(multipart("/api/users/kyc")
                .file(documentFile)
                .header("Authorization", "Bearer testToken")
                .param("documentType", "PASSPORT")
                .param("documentNumber", "ABC12345"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Validation Error: KYC is already approved"));
    }

    @Test
    void submitKyc_SystemError() throws Exception {
        MockMultipartFile documentFile = new MockMultipartFile("documentFile", "test.pdf", "application/pdf", "pdf".getBytes());

        when(jwtUtil.extractUserId(anyString())).thenReturn(UUID.randomUUID().toString());
        when(jwtUtil.extractEmail(anyString())).thenReturn("test@example.com");
        when(jwtUtil.extractRole(anyString())).thenReturn("USER");
        when(userService.buildKycRequest(anyString(), anyString(), any())).thenThrow(new IllegalStateException("Storage unavailable"));

        mockMvc.perform(multipart("/api/users/kyc")
                .file(documentFile)
                .header("Authorization", "Bearer testToken")
                .param("documentType", "PASSPORT")
                .param("documentNumber", "ABC12345"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Validation Error: Storage unavailable"));
    }

    @Test
    void getUserInternal_Found() throws Exception {
        UUID userId = UUID.randomUUID();
        com.wallet.user.entity.User user = new com.wallet.user.entity.User();
        user.setId(userId);
        user.setEmail("user@example.com");

        when(userService.findById(userId)).thenReturn(java.util.Optional.of(user));

        mockMvc.perform(get("/api/users/internal/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void getUserInternal_NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.findById(userId)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/users/internal/" + userId))
                .andExpect(status().isNotFound());
    }
}
