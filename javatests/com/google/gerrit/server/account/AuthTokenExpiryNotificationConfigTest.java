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
import static com.google.gerrit.server.account.AuthTokenExpiryNotificationConfig.AUTH;
import static com.google.gerrit.server.account.AuthTokenExpiryNotificationConfig.NOTIFY;
import static com.google.gerrit.server.account.AuthTokenExpiryNotificationConfig.NOTIFY_DAYS_BEFORE;
import static com.google.gerrit.server.account.AuthTokenExpiryNotificationConfig.TOKEN_EXPIRY;
import static org.junit.Assert.assertThrows;

import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class AuthTokenExpiryNotificationConfigTest {

  @Test
  public void defaultNotificationDays() throws Exception {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getNotificationDays()).containsExactly(0, 7, 14, 21);
  }

  @Test
  public void readsNotificationDaysFromConfig() throws Exception {
    Config config = new Config();
    config.setStringList(AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE, List.of("5", "10", "15"));
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.getNotificationDays()).containsExactly(5, 10, 15);
  }

  @Test
  public void invalidNotificationDaysThrows() throws Exception {
    Config config = new Config();
    config.setStringList(AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE, List.of("1", "b", "c"));

    assertThrows(ConfigInvalidException.class, () -> new AuthTokenExpiryNotificationConfig(config));
  }

  @Test
  public void featureIsEnabledByDefault() throws Exception {
    Config config = new Config();
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isTrue();
  }

  @Test
  public void featureCanBeDisabled() throws Exception {
    Config config = new Config();
    config.setBoolean(AUTH, TOKEN_EXPIRY, NOTIFY, false);
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);

    assertThat(notificationConfig.isEnabled()).isFalse();
  }

  @Test
  public void negativeValuesAreIgnored() throws Exception {
    Config config = new Config();
    config.setStringList(AUTH, TOKEN_EXPIRY, NOTIFY_DAYS_BEFORE, List.of("-5", "5", "10", "15"));
    AuthTokenExpiryNotificationConfig notificationConfig =
        new AuthTokenExpiryNotificationConfig(config);
    assertThat(notificationConfig.getNotificationDays()).containsExactly(5, 10, 15);
  }
}
