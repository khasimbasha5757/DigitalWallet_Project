package com.wallet.auth.config;

import com.wallet.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        String token = null;
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                logger.debug("Validating token for request: {}", request.getRequestURI());
                jwtUtil.validateToken(token);
                String userId = jwtUtil.extractUserId(token);
                logger.debug("Extracted userId from token: {}", userId);
                
                if (userId != null && !userId.isBlank()) {
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    String role = jwtUtil.extractRole(token);
                    logger.debug("Extracted role from token: {}", role);
                    
                    if (role != null && !role.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    }
                    // Downstream access control relies on userId as principal and role-based authorities.
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userId, null, authorities);
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.info("Authentication successful for user: {}", userId);
                } else {
                    logger.warn("UserId missing in token");
                }
            } catch (Exception e) {
                logger.error("JWT Validation failed in auth-service: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Invalid or expired token\"}");
                return;
            }
        } else if (token == null && request.getHeader("Authorization") != null) {
            logger.warn("Authorization header present but Bearer prefix missing");
        }
        filterChain.doFilter(request, response);
    }
}
