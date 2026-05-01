package com.wallet.auth.service;

import com.wallet.auth.dto.AuthRequest;
import com.wallet.auth.dto.AuthResponse;
import com.wallet.auth.dto.ChangePasswordRequest;
import com.wallet.auth.dto.RegisterRequest;
import com.wallet.auth.entity.UserCredential;
import com.wallet.auth.repository.UserCredentialRepository;
import com.wallet.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserCredentialRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private UserCredential userCredential;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest("testuser", "test@example.com", "password", "USER", "Test User", "1234567890");
        userCredential = new UserCredential(UUID.randomUUID(), "testuser", "test@example.com", "encodedPassword", "USER", "PENDING_KYC", "Test User", "1234567890");
    }

    @Test
    void saveUser_Success() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        String result = authService.saveUser(registerRequest);

        assertEquals("User registration successful", result);
        verify(repository, times(1)).saveAndFlush(any(UserCredential.class));
    }

    @Test
    void saveUser_UserAlreadyExists_Email() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(userCredential));

        assertThrows(RuntimeException.class, () -> authService.saveUser(registerRequest));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void login_Success() {
        AuthRequest authRequest = new AuthRequest("testuser", "password");
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.of(userCredential));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("testToken");

        AuthResponse response = authService.login(authRequest);

        assertNotNull(response);
        assertEquals("testToken", response.getToken());
        assertEquals(userCredential.getEmail(), response.getEmail());
    }

    @Test
    void login_Success_WithEmailAndTrimmedUsername() {
        AuthRequest authRequest = new AuthRequest(" test@example.com ", "password");
        when(repository.findByUsernameIgnoreCase("test@example.com")).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(userCredential));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("testToken");

        AuthResponse response = authService.login(authRequest);

        assertNotNull(response);
        assertEquals("testToken", response.getToken());
        assertEquals(userCredential.getEmail(), response.getEmail());
    }

    @Test
    void login_InvalidCredentials_UserNotFound() {
        AuthRequest authRequest = new AuthRequest("testuser", "password");
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.login(authRequest));
    }

    @Test
    void login_InvalidCredentials_WrongPassword() {
        AuthRequest authRequest = new AuthRequest("testuser", "wrongpassword");
        when(repository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.of(userCredential));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(RuntimeException.class, () -> authService.login(authRequest));
    }

    @Test
    void getProfile_Success() {
        UUID userId = userCredential.getId();
        when(repository.findById(userId)).thenReturn(Optional.of(userCredential));

        UserCredential result = authService.getProfile(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
    }

    @Test
    void getProfile_NotFound() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authService.getProfile(userId));
    }

    @Test
    void changePassword_Success() {
        UUID userId = userCredential.getId();
        ChangePasswordRequest request = new ChangePasswordRequest("password", "newPassword");
        when(repository.findById(userId)).thenReturn(Optional.of(userCredential));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncodedPassword");

        String result = authService.changePassword(userId, request);

        assertEquals("Password updated successfully.", result);
        assertEquals("newEncodedPassword", userCredential.getPassword());
        verify(repository).save(userCredential);
    }

    @Test
    void changePassword_CurrentPasswordMismatch() {
        UUID userId = userCredential.getId();
        ChangePasswordRequest request = new ChangePasswordRequest("wrong", "newPassword");
        when(repository.findById(userId)).thenReturn(Optional.of(userCredential));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.changePassword(userId, request));
        assertEquals("Current password is incorrect.", exception.getMessage());
    }
}
