// Copyright (C) 2019 The Android Open Source Project
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
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.gerrit.server.util.time.TimeUtil;

/** Sender that informs a user by email that the HTTP password of their account was updated. */
@AutoFactory
public class HttpPasswordUpdateEmailDecorator implements EmailDecorator {
  private OutgoingEmail email;

  private final IdentifiedUser user;
  private final String operation;
  private final MessageIdGenerator messageIdGenerator;

  public HttpPasswordUpdateEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, IdentifiedUser user, String operation) {
    this.messageIdGenerator = messageIdGenerator;
    this.user = user;
    this.operation = operation;
  }

  @Override
  public void init(OutgoingEmail email) {
    this.email = email;

    email.setHeader("Subject", "[Gerrit Code Review] HTTP password was " + operation);
    email.setMessageId(
        messageIdGenerator.fromReasonAccountIdAndTimestamp(
            "HTTP_password_change", user.getAccountId(), TimeUtil.now()));
    email.addByAccountId(RecipientType.TO, user.getAccountId());
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("email", getEmail());
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(user.getAccountId()));
    email.addSoyEmailDataParam("operation", operation);
    email.addSoyEmailDataParam("httpPasswordSettingsUrl", email.getSettingsUrl("HTTPCredentials"));

    email.appendText(email.textTemplate("HttpPasswordUpdate"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("HttpPasswordUpdateHtml"));
    }
  }

  private String getEmail() {
    return user.getAccount().preferredEmail();
  }
}
