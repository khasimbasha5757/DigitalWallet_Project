package com.wallet.gateway.filter;

import com.wallet.gateway.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            String path = exchange.getRequest().getURI().getPath();
            logger.info("Authentication Filter processing path: {}", path);

            // Only protected routes are forced through JWT validation here.
            if (config.isAuthRequired(path)) {
                logger.debug("Authentication required for path: {}", path);

                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    logger.warn("[GATEWAY] MISSING Authorization header for path: {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                String authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                logger.debug("Received Authorization header: {}", authHeader != null ? "PRESENT" : "NULL");

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                } else {
                    logger.warn("[GATEWAY] INVALID Authorization header format (missing Bearer prefix) for path: {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                try {
                    // The gateway validates the token once before the request is forwarded downstream.
                    jwtUtil.validateToken(authHeader);
                    logger.info("[GATEWAY] JWT Validation SUCCESS for path: {}", path);
                } catch (Exception e) {
                    logger.error("[GATEWAY] JWT Validation FAILED for path: {} | Error: {}", path, e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }
            } else {
                logger.info("Authentication NOT required for path: {}", path);
            }
            return chain.filter(exchange);
        });
    }

    public static class Config {
        public boolean isAuthRequired(String path) {
            boolean required = !path.contains("/api/auth/signup") && !path.contains("/api/auth/login")
                && !path.contains("/v3/api-docs") && !path.contains("/swagger-ui") 
                && !path.contains("/webjars") && !path.contains("/actuator");
            return required;
        }
    }
}
