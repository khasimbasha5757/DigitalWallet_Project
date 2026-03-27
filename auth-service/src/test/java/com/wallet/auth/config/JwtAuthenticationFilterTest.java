package com.wallet.auth.config;

import com.wallet.auth.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_AuthenticatesWhenUserIdExistsEvenWithoutRole() throws Exception {
        JwtUtil jwtUtil = mock(JwtUtil.class);
        doNothing().when(jwtUtil).validateToken("valid-token");
        when(jwtUtil.extractUserId("valid-token")).thenReturn("aacfde7e-3408-448a-8c14-2bf5a1bd0f02");
        when(jwtUtil.extractRole("valid-token")).thenReturn(null);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertEquals("aacfde7e-3408-448a-8c14-2bf5a1bd0f02", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().isEmpty());
    }
}
