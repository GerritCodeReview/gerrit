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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.Config;

/**
 * Configuration for auth token expiry notifications.
 *
 * <p>Defines when and how often to send notification emails to users before their authentication
 * tokens expire.
 */
@Singleton
public class AuthTokenExpiryNotificationConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int DEFAULT_START_DAYS = 21;
  private static final int DEFAULT_INTERVAL_DAYS = 7;

  private final List<Integer> notificationDays;
  private final boolean enabled;

  @Inject
  public AuthTokenExpiryNotificationConfig(@GerritServerConfig Config config) {
    String[] configNotificationDays =
        config.getStringList("auth", null, "tokenExpiryNoficationDaysBefore");
    if (configNotificationDays.length == 0) {
      this.notificationDays =
          IntStream.rangeClosed(0, DEFAULT_START_DAYS)
              .filter(i -> i % DEFAULT_INTERVAL_DAYS == 0)
              .boxed()
              .toList();
    } else {
      this.notificationDays =
          Arrays.asList(configNotificationDays).stream()
              .map(Integer::parseInt)
              .filter(i -> i >= 0)
              .toList();
      if (this.notificationDays.size() < configNotificationDays.length) {
        logger.atWarning().log(
            "Some invalid values were found in auth.tokenExpiryNoficationDaysBefore configuration"
                + " and were ignored. Only non-negative integers are allowed.");
      }
    }
    this.enabled = config.getBoolean("auth", null, "tokenExpiryNoficationEnabled", true);
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
   * Returns a list of days before expiration on which to send notifications.
   *
   * <p>Only used when {@link #isEnabled()} returns true.
   *
   * @return list of days (default: [0,7,14,21])
   */
  public List<Integer> getNotificationDays() {
    return notificationDays;
  }

  /**
   * Determines whether a notification should be sent for a token expiring at the given date.
   *
   * @param expirationDate the token expiration date
   * @return true if a notification should be sent, false otherwise
   */
  public boolean shouldBeNotified(Instant expirationDate) {
    Instant currentTime = Instant.now();
    if (!enabled) {
      return false;
    }

    // Check if token already expired
    if (expirationDate.isBefore(currentTime)) {
      return false;
    }

    long daysUntilExpiration =
        ChronoUnit.DAYS.between(currentTime.truncatedTo(ChronoUnit.DAYS), expirationDate);
    return notificationDays.contains((int) daysUntilExpiration);
  }
}
