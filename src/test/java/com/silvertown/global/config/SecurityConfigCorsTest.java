package com.silvertown.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

    @Test
    void allowsTransferPreflightWithConfirmationAndIdempotencyHeaders() throws IOException {
        SecurityConfig securityConfig = new SecurityConfig(null, null);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/transfers/00000000-0000-0000-0000-000000000001/execute");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Confirmation-Token, Idempotency-Key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new DefaultCorsProcessor().processRequest(
                securityConfig.corsConfigurationSource().getCorsConfiguration(request), request, response);

        assertTrue(allowed);
        String allowedHeaders = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertNotNull(allowedHeaders);
        assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("confirmation-token"));
        assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("idempotency-key"));
    }
}
