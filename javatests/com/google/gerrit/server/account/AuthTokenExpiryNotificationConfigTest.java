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
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class AuthTokenExpiryNotificationConfigTest {

  @Test
  public void defaultStartDaysIs21() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getStartDays()).isEqualTo(21);
  }

  @Test
  public void defaultIntervalDaysIs7() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getIntervalDays()).isEqualTo(7);
  }

  @Test
  public void readsStartDaysFromConfig() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 30);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getStartDays()).isEqualTo(30);
  }

  @Test
  public void readsIntervalDaysFromConfig() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 14);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getIntervalDays()).isEqualTo(14);
  }

  @Test
  public void readsBothConfigValues() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 30);
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 10);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getStartDays()).isEqualTo(30);
    assertThat(notificationConfig.getIntervalDays()).isEqualTo(10);
  }

  @Test
  public void negativeStartDaysDisablesFeature() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", -1);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void negativeIntervalDaysDisablesFeature() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", -1);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void zeroStartDaysDisablesFeature() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 0);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void zeroIntervalDaysDisablesFeature() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 0);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void featureIsEnabledByDefault() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isTrue();
  }

  @Test
  public void featureIsEnabledWithPositiveValues() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 30);
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 10);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isTrue();
  }

  @Test
  public void calculatesNotificationTimesForDefaultConfig() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    // Token expires in 30 days
    Instant expirationDate = Instant.now().plus(30, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    // Default: start=21, interval=7 -> notify at 21d, 14d, 7d before expiry
    assertThat(notificationTimes).hasSize(3);
    assertThat(notificationTimes.get(0)).isEqualTo(expirationDate.minus(21, ChronoUnit.DAYS));
    assertThat(notificationTimes.get(1)).isEqualTo(expirationDate.minus(14, ChronoUnit.DAYS));
    assertThat(notificationTimes.get(2)).isEqualTo(expirationDate.minus(7, ChronoUnit.DAYS));
  }

  @Test
  public void calculatesNotificationTimesForCustomConfig() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 30);
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 10);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    // Token expires in 35 days
    Instant expirationDate = Instant.now().plus(35, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    // start=30, interval=10 -> notify at 30d, 20d, 10d before expiry
    assertThat(notificationTimes).hasSize(3);
    assertThat(notificationTimes.get(0)).isEqualTo(expirationDate.minus(30, ChronoUnit.DAYS));
    assertThat(notificationTimes.get(1)).isEqualTo(expirationDate.minus(20, ChronoUnit.DAYS));
    assertThat(notificationTimes.get(2)).isEqualTo(expirationDate.minus(10, ChronoUnit.DAYS));
  }

  @Test
  public void notificationTimesStopAtZero() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 15);
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 10);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    Instant expirationDate = Instant.now().plus(20, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    // start=15, interval=10 -> notify at 15d, 5d before expiry (stops before going negative)
    assertThat(notificationTimes).hasSize(2);
    assertThat(notificationTimes.get(0)).isEqualTo(expirationDate.minus(15, ChronoUnit.DAYS));
    assertThat(notificationTimes.get(1)).isEqualTo(expirationDate.minus(5, ChronoUnit.DAYS));
  }

  @Test
  public void noNotificationsWhenExpirationIsBeforeStartDays() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    // Token expires in 10 days, but start is 21 days
    Instant expirationDate = Instant.now().plus(10, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    // No notifications if expiration is sooner than start days
    assertThat(notificationTimes).isEmpty();
  }

  @Test
  public void noNotificationsWhenAlreadyExpired() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    // Token expired 5 days ago
    Instant expirationDate = Instant.now().minus(5, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    assertThat(notificationTimes).isEmpty();
  }

  @Test
  public void singleNotificationWhenStartEqualsInterval() {
    Config config = new Config();
    config.setInt("auth", null, "tokenExpiryNotificationStartDays", 7);
    config.setInt("auth", null, "tokenExpiryNotificationIntervalDays", 7);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    Instant expirationDate = Instant.now().plus(30, ChronoUnit.DAYS);
    List<Instant> notificationTimes = notificationConfig.calculateNotificationTimes(expirationDate);

    // start=7, interval=7 -> only one notification at 7d before expiry
    assertThat(notificationTimes).hasSize(1);
    assertThat(notificationTimes.get(0)).isEqualTo(expirationDate.minus(7, ChronoUnit.DAYS));
  }
}
