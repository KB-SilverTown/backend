package com.silvertown.global.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.silvertown.domain.voice.websocket.VoiceStreamWebSocketPolicy;
import java.io.IOException;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

    private final CorsConfiguration corsConfiguration =
            new SecurityConfig(null, null, originPolicy())
                    .corsConfigurationSource()
                    .getCorsConfiguration(new MockHttpServletRequest());

    @Test
    void allowsTransferPreflightWithConfirmationAndIdempotencyHeaders() throws IOException {
        SecurityConfig securityConfig = new SecurityConfig(null, null, originPolicy());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "OPTIONS", "/api/transfers/00000000-0000-0000-0000-000000000001/execute");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(
                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                "Confirmation-Token, Idempotency-Key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new DefaultCorsProcessor().processRequest(
                securityConfig.corsConfigurationSource().getCorsConfiguration(request),
                request,
                response);

        assertTrue(allowed);
        String allowedHeaders = response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertNotNull(allowedHeaders);
        assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("confirmation-token"));
        assertTrue(allowedHeaders.toLowerCase(Locale.ROOT).contains("idempotency-key"));
    }

    @Test
    void allowsPreflightFromConfirmedFrontendOrigin() throws Exception {
        PreflightResult result = processPreflight("http://localhost:5173");

        assertThat(result.accepted(), is(true));
        assertThat(
                result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
                is("http://localhost:5173"));
    }

    @Test
    void allowsPreflightFromAndroidCapacitorWebView() throws Exception {
        PreflightResult result = processPreflight("https://localhost");

        assertThat(result.accepted(), is(true));
        assertThat(
                result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
                is("https://localhost"));
    }

    @Test
    void rejectsPreflightFromUnconfirmedOrigin() throws Exception {
        PreflightResult result = processPreflight("https://untrusted.example");

        assertThat(result.accepted(), is(false));
        assertThat(
                result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
                is(nullValue()));
    }

    @Test
    void rejectsAnotherLocalhostPortThatIsNotExplicitlyAllowed() throws Exception {
        PreflightResult result = processPreflight("http://localhost:4173");

        assertThat(result.accepted(), is(false));
        assertThat(
                result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
                is(nullValue()));
    }

    private PreflightResult processPreflight(String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean accepted = new DefaultCorsProcessor()
                .processRequest(corsConfiguration, request, response);

        return new PreflightResult(accepted, response);
    }

    private static VoiceStreamWebSocketPolicy originPolicy() {
        return new VoiceStreamWebSocketPolicy("http://localhost:5173,https://localhost");
    }

    private record PreflightResult(boolean accepted, MockHttpServletResponse response) {}
}
