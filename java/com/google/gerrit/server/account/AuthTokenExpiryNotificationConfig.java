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
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
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

  static final String AUTH = "auth";
  static final String TOKEN_EXPIRY = "token-expiry";
  static final String NOTIFY = "notify";
  static final String NOTIFY_DAYS_BEFORE = "notifyDaysBefore";

  private static final List<Integer> DEFAULT_NOTIFICATION_DAYS = List.of(0, 7, 14, 21);

  private final boolean enabled;
  private final List<Integer> notificationDays;

  @Inject
  public AuthTokenExpiryNotificationConfig(@GerritServerConfig Config config)
      throws ConfigInvalidException {
    this.enabled = config.getBoolean(AUTH, TOKEN_EXPIRY, NOTIFY, true);
    String[] configNotificationDays = config.getStringList(AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE);
    if (configNotificationDays.length == 0) {
      this.notificationDays = DEFAULT_NOTIFICATION_DAYS;
    } else {
      try {
        this.notificationDays =
            Arrays.stream(configNotificationDays)
                .map(Integer::parseInt)
                .filter(i -> i >= 0)
                .toList();
      } catch (NumberFormatException e) {
        throw new ConfigInvalidException(
            String.format(
                "%s.%s.%s has to be a list of integers.", AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE),
            e);
      }
      if (this.notificationDays.size() < configNotificationDays.length) {
        logger.atWarning().log(
            "Some invalid values were found in %s.%s.%s configuration"
                + " and were ignored. Only non-negative integers are allowed.",
            AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE);
      }
    }
  }

  /**
   * Returns whether token expiry notifications are enabled.
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
}
