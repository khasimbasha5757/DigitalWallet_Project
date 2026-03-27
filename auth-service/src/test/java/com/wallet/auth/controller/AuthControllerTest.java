package com.wallet.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.auth.dto.AuthRequest;
import com.wallet.auth.dto.AuthResponse;
import com.wallet.auth.dto.RegisterRequest;
import com.wallet.auth.entity.UserCredential;
import com.wallet.auth.service.AuthService;
import com.wallet.auth.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple controller unit tests
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void addNewUser_Success() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password", "USER", "Test User", "1234567890");
        when(authService.saveUser(any(RegisterRequest.class))).thenReturn("User registration successful");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("User registration successful"));
    }

    @Test
    void getToken_Success() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "password");
        AuthResponse response = new AuthResponse("token", "123", "test@example.com", "USER", "Test User", "1234567890");
        when(authService.login(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void getProfile_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCredential user = new UserCredential(userId, "testuser", "test@example.com", "pass", "USER", "ACTIVE", "Test User", "123");
        when(authService.getProfile(userId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/users/" + userId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void getProfile_NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.getProfile(userId)).thenThrow(new RuntimeException("User profile not found"));

        mockMvc.perform(get("/api/auth/users/" + userId + "/profile"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User profile not found"));
    }

    @Test
    @WithMockUser(username = "262e53eb-b506-4a69-8ddf-bcedced51746", roles = "USER")
    void getProfile_UsesAuthenticatedUserIdForNonAdmin() throws Exception {
        UUID requestedUserId = UUID.randomUUID();
        UUID authenticatedUserId = UUID.fromString("262e53eb-b506-4a69-8ddf-bcedced51746");
        UserCredential user = new UserCredential(authenticatedUserId, "testuser", "test@example.com", "pass", "USER",
                "ACTIVE", "Test User", "123");
        when(authService.getProfile(authenticatedUserId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/users/" + requestedUserId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));

        verify(authService).getProfile(authenticatedUserId);
    }
}
