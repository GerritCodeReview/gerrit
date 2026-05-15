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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.server.mail.EmailFactories;
import com.google.gerrit.server.mail.send.OutgoingEmail;
import java.time.Instant;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

public class AuthTokenExpiryNotifierAsyncSenderTest {
  private EmailFactories emailFactories;
  private OutgoingEmail outgoingEmail;
  private Account account;
  private AuthToken token;

  @Before
  public void setUp() throws Exception {
    emailFactories = mock(EmailFactories.class);
    outgoingEmail = mock(OutgoingEmail.class);
    account = Account.builder(Account.id(1), Instant.now()).setFullName("Test User").build();
    token =
        AuthToken.create(
            "token-123", "hashed-token", Optional.of(Instant.now().plusSeconds(86400 * 7)));
  }

  @Test
  public void asyncSenderSendsExpiredTokenEmail() throws Exception {
    when(emailFactories.createAuthTokenExpiredEmail(any(), any())).thenReturn(null);
    when(emailFactories.createOutgoingEmail(eq("AuthTokenExpired"), any()))
        .thenReturn(outgoingEmail);

    AuthTokenExpiryNotifier.AsyncSender sender =
        new AuthTokenExpiryNotifier.AsyncSender(
            emailFactories, account, token, "AuthTokenExpired");

    sender.run();

    verify(outgoingEmail).send();
  }

  @Test
  public void asyncSenderSendsWillExpireTokenEmail() throws Exception {
    when(emailFactories.createAuthTokenWillExpireEmail(any(), any())).thenReturn(null);
    when(emailFactories.createOutgoingEmail(eq("AuthTokenWillExpire"), any()))
        .thenReturn(outgoingEmail);

    AuthTokenExpiryNotifier.AsyncSender sender =
        new AuthTokenExpiryNotifier.AsyncSender(
            emailFactories, account, token, "AuthTokenWillExpire");

    sender.run();

    verify(outgoingEmail).send();
  }

  @Test
  public void asyncSenderHandlesEmailException() throws Exception {
    when(emailFactories.createAuthTokenExpiredEmail(any(), any())).thenReturn(null);
    when(emailFactories.createOutgoingEmail(any(), any())).thenReturn(outgoingEmail);
    doThrow(new EmailException("SMTP error")).when(outgoingEmail).send();

    AuthTokenExpiryNotifier.AsyncSender sender =
        new AuthTokenExpiryNotifier.AsyncSender(
            emailFactories, account, token, "AuthTokenExpired");

    sender.run();

    // Assert - exception was logged but not rethrown
    // (AsyncSender should catch and log EmailException)
  }

  @Test
  public void asyncSenderToStringReturnsDescription() {
    // Arrange
    AuthTokenExpiryNotifier.AsyncSender sender =
        new AuthTokenExpiryNotifier.AsyncSender(
            emailFactories, account, token, "AuthTokenExpired");

    String description = sender.toString();

    assertThat(description).contains("send-email");
    assertThat(description).contains("auth-token");
  }
}
