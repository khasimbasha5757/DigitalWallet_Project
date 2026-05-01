package com.wallet.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.auth.dto.AuthRequest;
import com.wallet.auth.dto.AuthResponse;
import com.wallet.auth.dto.ChangePasswordRequest;
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
import static org.mockito.ArgumentMatchers.eq;
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
        AuthResponse response = new AuthResponse("token", "123", "test@example.com", "USER", "Test User", "1234567890", "");
        when(authService.login(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void addNewUser_Failure() throws Exception {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password", "USER", "Test User", "1234567890");
        when(authService.saveUser(any(RegisterRequest.class))).thenThrow(new RuntimeException("Email already exists"));

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email already exists"));
    }

    @Test
    void getToken_Failure() throws Exception {
        AuthRequest request = new AuthRequest("testuser", "wrong-password");
        when(authService.login(any(AuthRequest.class))).thenThrow(new RuntimeException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid Authentication"));
    }

    @Test
    void validateToken_ReturnsValidMessage() throws Exception {
        mockMvc.perform(get("/api/auth/validate").param("token", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string("Token is valid"));
    }

    @Test
    void updateStatus_Success() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/auth/internal/users/" + userId + "/status")
                .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string("User status updated to ACTIVE"));

        verify(authService).updateUserStatus(userId, "ACTIVE");
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

    @Test
    @WithMockUser(username = "admin-user", roles = "ADMIN")
    void getProfile_AdminUsesRequestedUserId() throws Exception {
        UUID requestedUserId = UUID.randomUUID();
        UserCredential user = new UserCredential(requestedUserId, "admin-view", "test@example.com", "pass", "USER",
                "ACTIVE", "Test User", "123");
        when(authService.getProfile(requestedUserId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/users/" + requestedUserId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin-view"));

        verify(authService).getProfile(requestedUserId);
    }

    @Test
    @WithMockUser(username = "not-a-uuid", roles = "USER")
    void getProfile_InvalidPrincipalFallsBackToRequestedUserId() throws Exception {
        UUID requestedUserId = UUID.randomUUID();
        UserCredential user = new UserCredential(requestedUserId, "fallback-user", "test@example.com", "pass", "USER",
                "ACTIVE", "Test User", "123");
        when(authService.getProfile(requestedUserId)).thenReturn(user);

        mockMvc.perform(get("/api/auth/users/" + requestedUserId + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("fallback-user"));

        verify(authService).getProfile(requestedUserId);
    }

    @Test
    @WithMockUser(username = "262e53eb-b506-4a69-8ddf-bcedced51746", roles = "USER")
    void updateProfile_UsesAuthenticatedUserIdForNonAdmin() throws Exception {
        UUID requestedUserId = UUID.randomUUID();
        UUID authenticatedUserId = UUID.fromString("262e53eb-b506-4a69-8ddf-bcedced51746");
        UserCredential updated = new UserCredential(authenticatedUserId, "updated-user", "test@example.com", "pass",
                "USER", "ACTIVE", "Updated User", "999");
        when(authService.updateProfile(eq(authenticatedUserId), any())).thenReturn(updated);

        mockMvc.perform(post("/api/auth/users/" + requestedUserId + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Updated User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updated-user"));
    }

    @Test
    void updateProfile_NotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authService.updateProfile(eq(userId), any())).thenThrow(new RuntimeException("User profile not found"));

        mockMvc.perform(post("/api/auth/users/" + userId + "/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Updated User\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User profile not found"));
    }

    @Test
    @WithMockUser(username = "262e53eb-b506-4a69-8ddf-bcedced51746", roles = "USER")
    void changePassword_UsesAuthenticatedUserIdForNonAdmin() throws Exception {
        UUID requestedUserId = UUID.randomUUID();
        UUID authenticatedUserId = UUID.fromString("262e53eb-b506-4a69-8ddf-bcedced51746");
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword");
        when(authService.changePassword(eq(authenticatedUserId), any())).thenReturn("Password updated successfully.");

        mockMvc.perform(post("/api/auth/users/" + requestedUserId + "/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password updated successfully."));
    }

    @Test
    void changePassword_Failure() throws Exception {
        UUID userId = UUID.randomUUID();
        ChangePasswordRequest request = new ChangePasswordRequest("oldPassword", "newPassword");
        when(authService.changePassword(eq(userId), any())).thenThrow(new RuntimeException("Current password is incorrect."));

        mockMvc.perform(post("/api/auth/users/" + userId + "/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Current password is incorrect."));
    }
}
