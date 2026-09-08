package com.silvertown.global.security;

import java.io.IOException;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String accessToken = resolveAccessToken(request);
    if (accessToken != null && jwtTokenProvider.isValidAccessToken(accessToken)) {
      String userId = jwtTokenProvider.getAccessTokenUserId(accessToken).toString();
      SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
    filterChain.doFilter(request, response);
  }

  private String resolveAccessToken(HttpServletRequest request) {
    String authorization = request.getHeader(AUTHORIZATION_HEADER);
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String token = authorization.substring(BEARER_PREFIX.length()).trim();
    return token.isEmpty() ? null : token;
  }
}
