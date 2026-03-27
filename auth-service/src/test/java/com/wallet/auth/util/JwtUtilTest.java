package com.wallet.auth.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";
    private final long expiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
    }

    @Test
    void generateToken_Success() {
        String token = jwtUtil.generateToken("test@example.com", "USER", "123", "Test User", "1234567890");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractAllClaims_Success() {
        String token = jwtUtil.generateToken("test@example.com", "USER", "123", "Test User", "1234567890");
        Claims claims = jwtUtil.extractAllClaims(token);
        
        assertEquals("test@example.com", claims.getSubject());
        assertEquals("USER", claims.get("role"));
        assertEquals("123", claims.get("userId"));
    }

    @Test
    void extractUserId_Success() {
        String token = jwtUtil.generateToken("test@example.com", "USER", "123", "Test User", "1234567890");
        String userId = jwtUtil.extractUserId(token);
        assertEquals("123", userId);
    }

    @Test
    void validateToken_Success() {
        String token = jwtUtil.generateToken("test@example.com", "USER", "123", "Test User", "1234567890");
        assertDoesNotThrow(() -> jwtUtil.validateToken(token));
    }

    @Test
    void isTokenExpired_False() {
        String token = jwtUtil.generateToken("test@example.com", "USER", "123", "Test User", "1234567890");
        assertFalse(jwtUtil.isTokenExpired(token));
    }
}
