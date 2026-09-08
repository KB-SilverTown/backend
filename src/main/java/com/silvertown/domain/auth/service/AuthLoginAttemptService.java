package com.silvertown.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthLoginAttemptService {

  private final Clock clock;
  private final int maxFailures;
  private final Duration window;
  private final int maxTrackedLoginIds;
  private final Map<String, FailedAttempt> attempts = new LinkedHashMap<>(16, 0.75f, true);
  private final Object[] loginLocks = new Object[64];

  public AuthLoginAttemptService(
      Clock clock,
      @Value("${auth.login.max-failures:5}") int maxFailures,
      @Value("${auth.login.failure-window-seconds:300}") long windowSeconds,
      @Value("${auth.login.max-tracked-ids:10000}") int maxTrackedLoginIds) {
    this.clock = clock;
    this.maxFailures = maxFailures;
    this.window = Duration.ofSeconds(windowSeconds);
    this.maxTrackedLoginIds = maxTrackedLoginIds;
    for (int index = 0; index < loginLocks.length; index++) {
      loginLocks[index] = new Object();
    }
  }

  public boolean isBlocked(String loginId) {
    synchronized (attempts) {
      removeExpiredAttempts(clock.instant());
      FailedAttempt attempt = attempts.get(loginId);
      return attempt != null && attempt.count >= maxFailures;
    }
  }

  public void recordFailure(String loginId) {
    Instant now = clock.instant();
    synchronized (attempts) {
      removeExpiredAttempts(now);
      FailedAttempt current = attempts.get(loginId);
      if (current == null) {
        removeOldestAttemptIfFull();
        attempts.put(loginId, new FailedAttempt(1, now));
      } else {
        attempts.put(loginId, new FailedAttempt(current.count + 1, current.firstFailedAt));
      }
    }
  }

  public void clear(String loginId) {
    synchronized (attempts) {
      attempts.remove(loginId);
    }
  }

  public <T> T executeWithLoginLock(String loginId, Supplier<T> action) {
    Object lock = loginLocks[Math.floorMod(loginId.hashCode(), loginLocks.length)];
    synchronized (lock) {
      return action.get();
    }
  }

  @Scheduled(fixedDelayString = "${auth.login.failure-cleanup-millis:60000}")
  public void evictExpiredAttempts() {
    synchronized (attempts) {
      removeExpiredAttempts(clock.instant());
    }
  }

  int trackedAttemptCount() {
    synchronized (attempts) {
      return attempts.size();
    }
  }

  private void removeOldestAttemptIfFull() {
    if (attempts.size() < maxTrackedLoginIds) {
      return;
    }
    Iterator<String> iterator = attempts.keySet().iterator();
    if (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }

  private void removeExpiredAttempts(Instant now) {
    Iterator<Map.Entry<String, FailedAttempt>> iterator = attempts.entrySet().iterator();
    while (iterator.hasNext()) {
      FailedAttempt attempt = iterator.next().getValue();
      if (attempt.firstFailedAt.plus(window).isBefore(now)) {
        iterator.remove();
      }
    }
  }

  private static class FailedAttempt {

    private final int count;
    private final Instant firstFailedAt;

    private FailedAttempt(int count, Instant firstFailedAt) {
      this.count = count;
      this.firstFailedAt = firstFailedAt;
    }
  }
}
