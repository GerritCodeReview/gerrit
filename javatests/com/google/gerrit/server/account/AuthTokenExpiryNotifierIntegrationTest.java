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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbImpl;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class AuthTokenExpiryNotifierIntegrationTest {
  private AccountsNoteDbImpl accounts;
  private AuthTokenAccessor tokenAccessor;
  private EmailFactories emailFactories;
  private AuthTokenExpiryNotificationConfig config;
  private ExecutorService sendEmailExecutor;
  private OutgoingEmail outgoingEmail;
  private AuthTokenExpiryNotifier notifier;

  @Before
  public void setUp() throws Exception {
    accounts = mock(AccountsNoteDbImpl.class);
    tokenAccessor = mock(AuthTokenAccessor.class);
    emailFactories = mock(EmailFactories.class);
    config = mock(AuthTokenExpiryNotificationConfig.class);
    sendEmailExecutor = mock(ExecutorService.class);
    outgoingEmail = mock(OutgoingEmail.class);

    // Default: notifications enabled
    when(config.isEnabled()).thenReturn(true);

    notifier =
        new AuthTokenExpiryNotifier(
            accounts, tokenAccessor, emailFactories, config, sendEmailExecutor);

    when(config.calculateNotificationTimes(any())).thenReturn(ImmutableList.of(Instant.now()));
    when(emailFactories.createAuthTokenWillExpireEmail(any(), any())).thenReturn(null);
    when(emailFactories.createOutgoingEmail(any(), any())).thenReturn(outgoingEmail);
  }

  @Test
  public void runSubmitsEmailTasksToExecutor() throws Exception {
    AuthToken token =
        AuthToken.create("token-123", "hashed-token", Optional.of(Instant.now().plus(21, ChronoUnit.DAYS)));
    setUpAccount(ImmutableList.of(token));

    notifier.run();

    ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(sendEmailExecutor, times(1)).submit(taskCaptor.capture());
    verify(outgoingEmail, never()).send(); // Should not be called synchronously
  }

  @Test
  public void runDoesNotSubmitWhenNotificationsDisabled() throws Exception {
    AuthToken token =
        AuthToken.create("token-123", "hashed-token", Optional.of(Instant.now().plus(21, ChronoUnit.DAYS)));
    setUpAccount(ImmutableList.of(token));
    when(config.isEnabled()).thenReturn(false);

    notifier.run();

    verify(sendEmailExecutor, never()).submit(any(Runnable.class));
  }

  @Test
  public void runSubmitsMultipleEmailTasksForMultipleTokens() throws Exception {
    AuthToken token1 = AuthToken.create("token-1", "hash1", Optional.of(Instant.now().plus(21, ChronoUnit.DAYS)));
    AuthToken token2 = AuthToken.create("token-2", "hash2", Optional.of(Instant.now().plus(14, ChronoUnit.DAYS)));
    setUpAccount(ImmutableList.of(token1, token2));

    notifier.run();

    verify(sendEmailExecutor, times(2)).submit(any(Runnable.class));
  }

  private void setUpAccount(ImmutableList<AuthToken> tokens) throws Exception {
    Account account = Account.builder(Account.id(1), Instant.now()).setFullName("Test User").build();
    AccountState accountState = AccountState.forAccount(account);

    when(accounts.all()).thenReturn(ImmutableList.of(accountState));
    when(tokenAccessor.getTokens(account.id())).thenReturn(tokens);
  }
}
