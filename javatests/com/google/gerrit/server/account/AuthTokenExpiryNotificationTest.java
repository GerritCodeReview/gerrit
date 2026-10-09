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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AuthTokenExpiryNotificationTest {

  @Mock private Accounts accounts;
  @Mock private AuthTokenAccessor tokenAccessor;
  @Mock private EmailFactories emailFactories;
  @Mock private OutgoingEmail outgoingEmail;
  @Mock private EmailDecorator emailDecorator;

  private AuthTokenExpiryNotificationConfig defaultConfig;

  @Before
  public void setUp() throws Exception {
    defaultConfig = new AuthTokenExpiryNotificationConfig(new Config());
  }

  @Test
  public void suggestsNotificationOnConfiguredDays() {
    AuthTokenExpiryNotifier notifier = new AuthTokenExpiryNotifier(null, null, null, defaultConfig);

    Instant checkTime = Instant.now();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(1, ChronoUnit.HOURS))).isTrue();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(7, ChronoUnit.DAYS))).isTrue();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(14, ChronoUnit.DAYS))).isTrue();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(21, ChronoUnit.DAYS))).isTrue();
  }

  @Test
  public void doesNotSuggestNotificationForNonScheduledDays() {
    AuthTokenExpiryNotifier notifier = new AuthTokenExpiryNotifier(null, null, null, defaultConfig);

    Instant checkTime = Instant.now();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(3, ChronoUnit.DAYS))).isFalse();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.plus(30, ChronoUnit.DAYS))).isFalse();
  }

  @Test
  public void doesNotSuggestNotificationWhenExpired() {
    AuthTokenExpiryNotifier notifier = new AuthTokenExpiryNotifier(null, null, null, defaultConfig);

    Instant checkTime = Instant.now();
    assertThat(notifier.shouldBeNotified(checkTime, checkTime.minus(3, ChronoUnit.DAYS))).isFalse();
  }

  @Test
  public void shouldNotifyExpiredReturnsTrueWithinOneDayWindow() {
    Instant checkTime = Instant.now();
    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyExpired(
                checkTime, checkTime.minus(1, ChronoUnit.MINUTES)))
        .isTrue();
    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyExpired(
                checkTime, checkTime.minus(23, ChronoUnit.HOURS)))
        .isTrue();
  }

  @Test
  public void shouldNotifyExpiredReturnsFalseOutsideOneDayWindow() {
    Instant checkTime = Instant.now();
    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyExpired(
                checkTime, checkTime.minus(25, ChronoUnit.HOURS)))
        .isFalse();
  }

  @Test
  public void shouldNotifyExpiredReturnsFalseForFutureExpiry() {
    Instant checkTime = Instant.now();
    assertThat(
            AuthTokenExpiryNotifier.shouldNotifyExpired(
                checkTime, checkTime.plus(1, ChronoUnit.HOURS)))
        .isFalse();
  }

  @Test
  public void runSkipsAllWhenDisabled() throws Exception {
    Config cfg = new Config();
    cfg.setBoolean(
        AuthTokenExpiryNotificationConfig.AUTH,
        AuthTokenExpiryNotificationConfig.TOKEN_EXPIRY,
        AuthTokenExpiryNotificationConfig.NOTIFY,
        false);
    AuthTokenExpiryNotificationConfig disabledConfig = new AuthTokenExpiryNotificationConfig(cfg);

    AuthTokenExpiryNotifier notifier =
        new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, disabledConfig);
    notifier.run();

    verify(accounts, never()).all();
    verify(tokenAccessor, never()).getTokens(any(Account.Id.class));
    verify(emailFactories, never()).createOutgoingEmail(any(), any());
  }

  @Test
  public void runSendsEmailForExpiringToken() throws Exception {
    Account account = buildAccount(1001, "Alice");
    AccountState accountState = AccountState.forAccount(account);

    Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
    AuthToken token = AuthToken.create("token-alice", "hashed", Optional.of(expiry));

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of(token));
    when(emailFactories.createAuthTokenWillExpireEmail(account, token)).thenReturn(emailDecorator);
    when(emailFactories.createOutgoingEmail(
            eq(EmailFactories.AUTH_TOKEN_WILL_EXPIRE), eq(emailDecorator)))
        .thenReturn(outgoingEmail);

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(outgoingEmail, times(1)).sendAsync();
  }

  @Test
  public void runDoesNotSendEmailWhenExpiryNotOnScheduledDay() throws Exception {
    Account account = buildAccount(1002, "Bob");
    AccountState accountState = AccountState.forAccount(account);

    // 5 days away — not in the default schedule [0, 7, 14, 21]
    Instant expiry = Instant.now().plus(5, ChronoUnit.DAYS);
    AuthToken token = AuthToken.create("token-bob", "hashed", Optional.of(expiry));

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of(token));

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(emailFactories, never()).createOutgoingEmail(any(), any());
  }

  @Test
  public void runSkipsTokensWithoutExpirationDate() throws Exception {
    Account account = buildAccount(1003, "Carol");
    AccountState accountState = AccountState.forAccount(account);

    AuthToken token = AuthToken.create("token-carol", "hashed", Optional.empty());

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of(token));

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(emailFactories, never()).createOutgoingEmail(any(), any());
  }

  @Test
  public void runSendsExpiryEmailForRecentlyExpiredToken() throws Exception {
    Account account = buildAccount(1004, "Dave");
    AccountState accountState = AccountState.forAccount(account);

    // Expired 1 hour ago — within the 24-hour expiry-notification window
    Instant expiry = Instant.now().minus(1, ChronoUnit.HOURS);
    AuthToken token = AuthToken.create("token-dave", "hashed", Optional.of(expiry));

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of(token));
    when(emailFactories.createAuthTokenExpiredEmail(account, token)).thenReturn(emailDecorator);
    when(emailFactories.createOutgoingEmail(
            eq(EmailFactories.AUTH_TOKEN_EXPIRED), eq(emailDecorator)))
        .thenReturn(outgoingEmail);

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(outgoingEmail, times(1)).sendAsync();
  }

  @Test
  public void runSkipsTokenExpiredMoreThanOneDayAgo() throws Exception {
    Account account = buildAccount(1011, "Eve");
    AccountState accountState = AccountState.forAccount(account);

    // Expired 25 hours ago — outside the expiry-notification window
    Instant expiry = Instant.now().minus(25, ChronoUnit.HOURS);
    AuthToken token = AuthToken.create("token-eve", "hashed", Optional.of(expiry));

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of(token));

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(emailFactories, never()).createOutgoingEmail(any(), any());
  }

  @Test
  public void runHandlesMultipleAccountsAndTokens() throws Exception {
    Account alice = buildAccount(1005, "Alice");
    Account bob = buildAccount(1006, "Bob");
    AccountState aliceState = AccountState.forAccount(alice);
    AccountState bobState = AccountState.forAccount(bob);

    // Alice has two tokens: one on-schedule (+7d+1h bucket), one off-schedule (+5d)
    Instant aliceExpiryScheduled = Instant.now().plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
    Instant aliceExpiryOff = Instant.now().plus(5, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
    AuthToken aliceToken1 =
        AuthToken.create("alice-token-1", "hashed", Optional.of(aliceExpiryScheduled));
    AuthToken aliceToken2 =
        AuthToken.create("alice-token-2", "hashed", Optional.of(aliceExpiryOff));

    // Bob has one on-schedule token
    Instant bobExpiry = Instant.now().plus(14, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
    AuthToken bobToken = AuthToken.create("bob-token", "hashed", Optional.of(bobExpiry));

    EmailDecorator aliceDecorator = mock(EmailDecorator.class);
    EmailDecorator bobDecorator = mock(EmailDecorator.class);
    OutgoingEmail aliceEmail = mock(OutgoingEmail.class);
    OutgoingEmail bobEmail = mock(OutgoingEmail.class);

    when(accounts.all()).thenReturn(List.of(aliceState, bobState));
    when(tokenAccessor.getTokens(alice.id())).thenReturn(List.of(aliceToken1, aliceToken2));
    when(tokenAccessor.getTokens(bob.id())).thenReturn(List.of(bobToken));

    when(emailFactories.createAuthTokenWillExpireEmail(alice, aliceToken1))
        .thenReturn(aliceDecorator);
    when(emailFactories.createOutgoingEmail(
            eq(EmailFactories.AUTH_TOKEN_WILL_EXPIRE), eq(aliceDecorator)))
        .thenReturn(aliceEmail);

    when(emailFactories.createAuthTokenWillExpireEmail(bob, bobToken)).thenReturn(bobDecorator);
    when(emailFactories.createOutgoingEmail(
            eq(EmailFactories.AUTH_TOKEN_WILL_EXPIRE), eq(bobDecorator)))
        .thenReturn(bobEmail);

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(aliceEmail, times(1)).sendAsync();
    verify(bobEmail, times(1)).sendAsync();
    // aliceToken2 is off-schedule — no email
    verify(emailFactories, never()).createAuthTokenWillExpireEmail(alice, aliceToken2);
  }

  @Test
  public void runHandlesAccountWithNoTokens() throws Exception {
    Account account = buildAccount(1009, "Empty");
    AccountState accountState = AccountState.forAccount(account);

    when(accounts.all()).thenReturn(List.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(List.of());

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(emailFactories, never()).createOutgoingEmail(any(), any());
  }

  @Test
  public void runDoesNotThrowWhenTokenAccessorFails() throws Exception {
    Account account = buildAccount(1010, "Failing");
    Account bob = buildAccount(1011, "Bob");
    AccountState accountState = AccountState.forAccount(account);
    AccountState bobState = AccountState.forAccount(bob);

    Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS);
    AuthToken bobToken = AuthToken.create("bob-token", "hashed", Optional.of(expiry));

    EmailDecorator bobDecorator = mock(EmailDecorator.class);
    OutgoingEmail bobEmail = mock(OutgoingEmail.class);

    when(accounts.all()).thenReturn(List.of(accountState, bobState));
    when(tokenAccessor.getTokens(account.id()))
        .thenThrow(new ConfigInvalidException("corrupt config"));
    when(tokenAccessor.getTokens(bob.id())).thenReturn(List.of(bobToken));

    when(emailFactories.createAuthTokenWillExpireEmail(bob, bobToken)).thenReturn(bobDecorator);
    when(emailFactories.createOutgoingEmail(
            eq(EmailFactories.AUTH_TOKEN_WILL_EXPIRE), eq(bobDecorator)))
        .thenReturn(bobEmail);

    new AuthTokenExpiryNotifier(accounts, tokenAccessor, emailFactories, defaultConfig).run();

    verify(bobEmail, times(1)).sendAsync();
  }

  private static Account buildAccount(int id, String fullName) {
    return Account.builder(Account.id(id), Instant.EPOCH).setFullName(fullName).build();
  }
}
