package com.silvertown.global.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

class SecurityConfigCorsTest {

  private final CorsConfiguration corsConfiguration =
      new SecurityConfig(null, null)
          .corsConfigurationSource()
          .getCorsConfiguration(new MockHttpServletRequest());

  @Test
  void allowsPreflightFromConfirmedFrontendOrigin() throws Exception {
    PreflightResult result = processPreflight("http://localhost:4173");

    assertThat(result.accepted(), is(true));
    assertThat(result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
        is("http://localhost:4173"));
  }

  @Test
  void allowsPreflightFromCapacitorFrontend() throws Exception {
    PreflightResult result = processPreflight("capacitor://localhost");

    assertThat(result.accepted(), is(true));
    assertThat(result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
        is("capacitor://localhost"));
  }

  @Test
  void rejectsPreflightFromUnconfirmedOrigin() throws Exception {
    PreflightResult result = processPreflight("https://untrusted.example");

    assertThat(result.accepted(), is(false));
    assertThat(result.response().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN),
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

  private record PreflightResult(boolean accepted, MockHttpServletResponse response) {}
}
