// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.account.AuthToken;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.gerrit.server.util.time.TimeUtil;

/** Sender that informs a user by email that an auth token will expire soon. */
@AutoFactory
public class AuthTokenExpiredEmailDecorator implements EmailDecorator {
  private OutgoingEmail email;

  private final Account account;
  private final AuthToken token;
  private final MessageIdGenerator messageIdGenerator;

  public AuthTokenExpiredEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, Account account, AuthToken token) {
    this.messageIdGenerator = messageIdGenerator;
    this.account = account;
    this.token = token;
  }

  @Override
  public void init(OutgoingEmail email) {
    this.email = email;

    email.setHeader(
        "Subject",
        String.format("[Gerrit Code Review] Authentication token '%s' has expired.", token.id()));
    email.setMessageId(
        messageIdGenerator.fromReasonAccountIdAndTimestamp(
            "Auth_token_expired", account.id(), TimeUtil.now()));
    email.addByAccountId(RecipientType.TO, account.id());
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("email", account.preferredEmail());
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(account.id()));
    email.addSoyEmailDataParam("expirationDate", token.expirationDate().get().toString());
    email.addSoyEmailDataParam("tokenId", token.id());
    email.addSoyEmailDataParam("authTokenSettingsUrl", email.getSettingsUrl("HTTPCredentials"));

    email.appendText(email.textTemplate("AuthTokenExpired"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("AuthTokenExpiredHtml"));
    }
  }
}
