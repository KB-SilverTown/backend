package com.silvertown.domain.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthLoginAttemptServiceTest {

  @Test
  void blocksAfterConfiguredFailureCountAndClearsAfterSuccess() {
    AuthLoginAttemptService service = new AuthLoginAttemptService(
        Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC), 3, 300, 10);

    service.recordFailure("senior01");
    service.recordFailure("senior01");
    assertFalse(service.isBlocked("senior01"));

    service.recordFailure("senior01");
    assertTrue(service.isBlocked("senior01"));

    service.clear("senior01");
    assertFalse(service.isBlocked("senior01"));
  }

  @Test
  void evictsExpiredAttemptsAndBoundsTrackedLoginIds() {
    MutableClock clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
    AuthLoginAttemptService service = new AuthLoginAttemptService(clock, 3, 300, 2);

    service.recordFailure("senior01");
    service.recordFailure("senior02");
    service.recordFailure("senior03");
    assertEquals(2, service.trackedAttemptCount());

    clock.advance(Duration.ofSeconds(301));
    service.evictExpiredAttempts();
    assertEquals(0, service.trackedAttemptCount());
  }

  private static class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
