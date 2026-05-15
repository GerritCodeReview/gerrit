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
  public void defaultNotificationDays() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getNotificationDays()).containsExactly(0, 7, 14, 21);
  }

  @Test
  public void readsNotificationDaysFromConfig() {
    Config config = new Config();
    config.setStringList("auth", null, "tokenExpiryNoficationDaysBefore", List.of("5", "10", "15"));
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getNotificationDays()).containsExactly(5, 10, 15);
  }

  @Test
  public void featureIsEnabledByDefault() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isTrue();
  }

  @Test
  public void featureCanBeDisabled() {
    Config config = new Config();
    config.setBoolean("auth", null, "tokenExpiryNoficationEnabled", false);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void negativeValuesAreIgnored() {
    Config config = new Config();
    config.setStringList(
        "auth", null, "tokenExpiryNoficationDaysBefore", List.of("-5", "5", "10", "15"));
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);
    assertThat(notificationConfig.getNotificationDays()).containsExactly(5, 10, 15);
  }

  @Test
  public void doesNotSuggestNotificationWhenDisabled() {
    Config config = new Config();
    config.setBoolean("auth", null, "tokenExpiryNoficationEnabled", false);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);
    Instant checkTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(7, ChronoUnit.DAYS)))
        .isFalse();
  }

  @Test
  public void suggestsNotificationOnConfiguredDays() {
    // Use default config with notification days [0,7,14,21]
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    Instant checkTime = Instant.now().truncatedTo(ChronoUnit.DAYS);

    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(1, ChronoUnit.HOURS)))
        .isTrue();
    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(7, ChronoUnit.DAYS)))
        .isTrue();
    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(14, ChronoUnit.DAYS)))
        .isTrue();
    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(21, ChronoUnit.DAYS)))
        .isTrue();

    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(3, ChronoUnit.DAYS)))
        .isFalse();
    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.plus(30, ChronoUnit.DAYS)))
        .isFalse();
  }

  @Test
  public void doesNotSuggestNotificationWhenExpired() {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);
    Instant checkTime = Instant.now().truncatedTo(ChronoUnit.DAYS);
    assertThat(notificationConfig.shouldBeNotified(checkTime, checkTime.minus(3, ChronoUnit.DAYS)))
        .isFalse();
  }
}
