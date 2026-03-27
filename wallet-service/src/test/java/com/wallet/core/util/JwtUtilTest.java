package com.wallet.core.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "aW1wb3J0YW50X3NlY3JldF9mb3Jfand0X3NpZ25pbmdfZG9tYWluX3dhbGxldA==";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
    }

    private String createTestToken(String subject, Map<String, Object> claims) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void extractUserId_Success() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "12345");
        String token = createTestToken("test@example.com", claims);
        
        assertEquals("12345", jwtUtil.extractUserId(token));
    }

    @Test
    void extractEmail_Success() {
        String token = createTestToken("test@example.com", new HashMap<>());
        assertEquals("test@example.com", jwtUtil.extractEmail(token));
    }

    @Test
    void extractRole_Success() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        String token = createTestToken("test@example.com", claims);
        
        assertEquals("USER", jwtUtil.extractRole(token));
    }
}
