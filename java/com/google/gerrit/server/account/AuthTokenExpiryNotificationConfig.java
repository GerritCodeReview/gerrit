// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.account;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;

/**
 * Configuration for auth token expiry notifications.
 *
 * <p>Defines when and how often to send notification emails to users before their authentication
 * tokens expire.
 */
@Singleton
public class AuthTokenExpiryNotificationConfig {
  private static final int DEFAULT_START_DAYS = 21;
  private static final int DEFAULT_INTERVAL_DAYS = 7;

  private final int startDays;
  private final int intervalDays;
  private final boolean enabled;

  @Inject
  public AuthTokenExpiryNotificationConfig(@GerritServerConfig Config config) {
    this.startDays =
        config.getInt("auth", null, "tokenExpiryNotificationStartDays", DEFAULT_START_DAYS);
    this.intervalDays =
        config.getInt("auth", null, "tokenExpiryNotificationIntervalDays", DEFAULT_INTERVAL_DAYS);
    this.enabled = this.startDays > 0 && this.intervalDays > 0;
  }

  /**
   * Returns whether token expiry notifications are enabled.
   *
   * <p>The feature is disabled if either startDays or intervalDays is set to a non-positive value
   * (zero or negative).
   *
   * @return true if enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Returns the number of days before token expiration to start sending notifications.
   *
   * <p>Only valid when {@link #isEnabled()} returns true.
   *
   * @return number of days (default: 21)
   */
  public int getStartDays() {
    return startDays;
  }

  /**
   * Returns the interval in days between repeated notifications.
   *
   * <p>Only valid when {@link #isEnabled()} returns true.
   *
   * @return interval in days (default: 7)
   */
  public int getIntervalDays() {
    return intervalDays;
  }

  /**
   * Calculates all notification times for a token expiring at the given date.
   *
   * <p>Notifications are scheduled at: startDays, startDays-interval, startDays-2*interval, ...
   * before the expiration date, stopping when the next notification would be at or after the
   * expiration date.
   *
   * <p>Returns an empty list if:
   *
   * <ul>
   *   <li>The feature is disabled ({@link #isEnabled()} returns false)
   *   <li>The token has already expired
   * </ul>
   *
   * @param expirationDate the token expiration date
   * @return list of notification times in chronological order (earliest first)
   */
  public List<Instant> calculateNotificationTimes(Instant expirationDate) {
    if (!enabled) {
      return ImmutableList.of();
    }

    Instant now = Instant.now();

    // Check if token already expired
    if (expirationDate.isBefore(now)) {
      return ImmutableList.of();
    }

    List<Instant> notificationTimes = new ArrayList<>();
    long daysBeforeExpiry = startDays;

    while (daysBeforeExpiry > 0) {
      Instant notificationTime = expirationDate.minus(daysBeforeExpiry, ChronoUnit.DAYS);
      notificationTimes.add(notificationTime);
      daysBeforeExpiry -= intervalDays;
    }

    return ImmutableList.copyOf(notificationTimes);
  }
}
