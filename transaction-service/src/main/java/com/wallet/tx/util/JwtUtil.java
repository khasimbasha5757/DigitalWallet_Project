package com.wallet.tx.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    public Claims extractAllClaims(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).get("userId", String.class);
    }

    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
