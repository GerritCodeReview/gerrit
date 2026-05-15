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

package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AuthTokenAccessor;
import com.google.gerrit.server.account.AuthTokenExpiryNotifier;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.Test;

/**
 * Integration tests for {@link AuthTokenExpiryNotifier} using real Gerrit infrastructure.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>The notifier handles various configurations correctly
 *   <li>The notifier runs without errors in various scenarios
 *   <li>The integration with the email system works correctly
 * </ul>
 *
 * <p>Note: Email content verification is limited because FakeEmailSender may not capture async
 * emails reliably. The unit tests verify the notification logic, while these integration tests
 * ensure the notifier runs without errors.
 */
public class AuthTokenExpiryNotifierIT extends AbstractDaemonTest {

  @Inject private AuthTokenExpiryNotifier notifier;
  @Inject private AuthTokenAccessor authTokenAccessor;

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void notifierDoesNotSendEmailsWithNoTokens() throws Exception {
    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void emailSentForTokenExpiringIn21Days() throws Exception {
    Account.Id accountId = accountCreator.user1().id();
    Instant expiration = Instant.now().plusSeconds(21 * 24 * 60 * 60 - 60 * 60); // 21 days - 1 hour
    @SuppressWarnings("unused")
    var unused =
        authTokenAccessor.addPlainToken(
            accountId, "token21d", "plaintoken", Optional.of(expiration));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).contains("will expire");
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void emailSentForExpiredToken() throws Exception {
    // Create account with token expired 12 hours ago
    Account.Id accountId = accountCreator.user1().id();
    Instant expiration = Instant.now().minusSeconds(12 * 60 * 60);
    @SuppressWarnings("unused")
    var unused =
        authTokenAccessor.addPlainToken(
            accountId, "tokenexpired", "plaintoken", Optional.of(expiration));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).body()).contains("has expired");
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void multipleEmailsSentForMultipleTokens() throws Exception {
    Account.Id accountId = accountCreator.user1().id();
    Instant expiration21d = Instant.now().plusSeconds(21 * 24 * 60 * 60 - 60 * 60);
    Instant expiration14d = Instant.now().plusSeconds(14 * 24 * 60 * 60 - 60 * 60);

    @SuppressWarnings("unused")
    var token1 =
        authTokenAccessor.addPlainToken(
            accountId, "token21d", "plaintoken1", Optional.of(expiration21d));
    @SuppressWarnings("unused")
    var token2 =
        authTokenAccessor.addPlainToken(
            accountId, "token14d", "plaintoken2", Optional.of(expiration14d));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(2);
    assertThat(sender.getMessages().get(0).body()).contains("will expire");
    assertThat(sender.getMessages().get(1).body()).contains("will expire");
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void multipleEmailsSentForMultipleAccounts() throws Exception {
    Account.Id accountId1 = accountCreator.user1().id();
    Account.Id accountId2 = accountCreator.user2().id();
    Instant expiration = Instant.now().plus(21, ChronoUnit.DAYS).minus(1, ChronoUnit.HOURS);

    @SuppressWarnings("unused")
    var token1 =
        authTokenAccessor.addPlainToken(
            accountId1, "tokenA", "plaintoken1", Optional.of(expiration));
    @SuppressWarnings("unused")
    var token2 =
        authTokenAccessor.addPlainToken(
            accountId2, "tokenB", "plaintoken2", Optional.of(expiration));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).hasSize(2);
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void notifierHandlesTokensWithoutExpiration() throws Exception {
    Account.Id accountId = accountCreator.user1().id();
    @SuppressWarnings("unused")
    var unused =
        authTokenAccessor.addPlainToken(
            accountId, "perpetual-token", "plaintoken", Optional.empty());

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  @GerritConfig(name = "auth.tokenExpiryNotificationStartDays", value = "21")
  @GerritConfig(name = "auth.tokenExpiryNotificationIntervalDays", value = "7")
  public void notifierHandlesTokenExpiringFarInFuture() throws Exception {
    Account.Id accountId = accountCreator.user1().id();
    Instant expiration = Instant.now().plusSeconds(60 * 24 * 60 * 60);
    @SuppressWarnings("unused")
    var unused =
        authTokenAccessor.addPlainToken(
            accountId, "token60d", "plaintoken", Optional.of(expiration));

    sender.clear();
    notifier.run();
    assertThat(sender.getMessages()).isEmpty();
  }
}
