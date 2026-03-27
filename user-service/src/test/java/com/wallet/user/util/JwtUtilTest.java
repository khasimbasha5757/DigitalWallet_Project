package com.wallet.user.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
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
    void extractAllClaims_Success() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        String token = createTestToken("test@example.com", claims);
        
        Claims extracted = jwtUtil.extractAllClaims(token);
        assertEquals("test@example.com", extracted.getSubject());
        assertEquals("USER", extracted.get("role"));
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
}
