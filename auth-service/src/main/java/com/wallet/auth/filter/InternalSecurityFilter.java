package com.wallet.auth.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class InternalSecurityFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(InternalSecurityFilter.class);
    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Gateway-Token";
    public static final String INTERNAL_SECRET_VALUE = "DigitalWalletInternalSecret2026";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // Documentation and health endpoints stay directly reachable for tooling and monitoring.
        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui") || path.contains("/webjars") || path.contains("/actuator")) {
            chain.doFilter(request, response);
            return;
        }

        // All business endpoints are expected to arrive through the gateway with the shared internal header.
        String secretToken = httpRequest.getHeader(INTERNAL_SECRET_HEADER);
        logger.info("Access attempt to {} | Internal Token Present: {}", path, (secretToken != null));

        if (INTERNAL_SECRET_VALUE.equals(secretToken)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("BLOCKED direct access to {} | Token: {}", path, secretToken);
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Direct access is forbidden. Use API Gateway (port 8090).");
        }
    }
}
