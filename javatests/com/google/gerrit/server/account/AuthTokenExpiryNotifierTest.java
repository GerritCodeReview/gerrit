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

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;

public class AuthTokenExpiryNotifierTest {
  private static final Instant NOW = Instant.now();
  private static final Instant LOWER_BOUND = NOW.minus(1, ChronoUnit.DAYS);
  private static final Instant UPPER_BOUND = NOW.plus(1, ChronoUnit.HOURS);

  @Test
  public void shouldNotifyForTokenReturnsTrueWhenAnyNotificationIsToday() {
    Instant expirationDate = NOW.plus(21, ChronoUnit.DAYS);
    // Notification times: 21d, 14d, 7d from NOW
    List<Instant> notificationTimes =
        java.util.Arrays.asList(
            expirationDate.minus(21, ChronoUnit.DAYS),
            expirationDate.minus(14, ChronoUnit.DAYS),
            expirationDate.minus(7, ChronoUnit.DAYS));

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isTrue();
  }

  @Test
  public void shouldNotifyForTokenReturnsFalseWhenAllNotificationsInFuture() {
    Instant expirationDate = NOW.plus(100, ChronoUnit.DAYS);
    // All notification times are far in the future
    List<Instant> notificationTimes =
        java.util.Arrays.asList(
            expirationDate.minus(30, ChronoUnit.DAYS),
            expirationDate.minus(20, ChronoUnit.DAYS),
            expirationDate.minus(10, ChronoUnit.DAYS));

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isFalse();
  }

  @Test
  public void shouldNotifyForTokenReturnsFalseWhenAllNotificationsInPast() {
    // All notification times were more than 24 hours ago
    List<Instant> notificationTimes =
        java.util.Arrays.asList(
            NOW.minus(30, ChronoUnit.DAYS),
            NOW.minus(20, ChronoUnit.DAYS),
            NOW.minus(10, ChronoUnit.DAYS));

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isFalse();
  }

  @Test
  public void shouldNotifyForTokenReturnsFalseForEmptyList() {
    List<Instant> notificationTimes = java.util.Collections.emptyList();

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isFalse();
  }

  @Test
  public void shouldNotifyForTokenReturnsTrueForFirstNotificationToday() {
    // First notification is today, others in the future
    List<Instant> notificationTimes =
        java.util.Arrays.asList(
            NOW.minus(1, ChronoUnit.HOURS),
            NOW.plus(7, ChronoUnit.DAYS),
            NOW.plus(14, ChronoUnit.DAYS));

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isTrue();
  }

  @Test
  public void shouldNotifyForTokenReturnsTrueForLastNotificationToday() {
    // Last notification is today, others in the past
    List<Instant> notificationTimes =
        java.util.Arrays.asList(
            NOW.minus(30, ChronoUnit.DAYS),
            NOW.minus(20, ChronoUnit.DAYS),
            NOW.minus(1, ChronoUnit.HOURS));

    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyForToken(
                notificationTimes, NOW, LOWER_BOUND, UPPER_BOUND))
        .isTrue();
  }
}
