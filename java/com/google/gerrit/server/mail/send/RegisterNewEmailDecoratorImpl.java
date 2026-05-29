// Copyright (C) 2009 The Android Open Source Project
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

import static java.util.Objects.requireNonNull;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.EmailTokenVerifier;

/**
 * Sender that informs a user by email about the registration of a new email address for their
 * account.
 */
@AutoFactory
public class RegisterNewEmailDecoratorImpl implements RegisterNewEmailDecorator {
  private OutgoingEmail email;
  private final EmailArguments args;
  private final EmailTokenVerifier tokenVerifier;
  private final IdentifiedUser user;
  private final String addr;
  private String emailToken;

  RegisterNewEmailDecoratorImpl(
      @Provided EmailArguments args,
      @Provided EmailTokenVerifier tokenVerifier,
      @Provided IdentifiedUser callingUser,
      final String address) {
    this.args = args;
    this.tokenVerifier = tokenVerifier;
    this.user = callingUser;
    this.addr = address;
  }

  @Override
  public void init(OutgoingEmail email) {
    this.email = email;

    email.setHeader("Subject", "[Gerrit Code Review] Email Verification");
    email.addByEmail(RecipientType.TO, Address.create(addr));
  }

  @Override
  public boolean isAllowed() {
    return args.emailSender.canEmail(addr);
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(user.getAccountId()));
    email.addSoyEmailDataParam("emailRegistrationLink", getEmailRegistrationLink());

    email.appendText(email.textTemplate("RegisterNewEmail"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("RegisterNewEmailHtml"));
    }
  }

  private String getEmailRegistrationLink() {
    if (emailToken == null) {
      emailToken = requireNonNull(tokenVerifier.encode(user.getAccountId(), addr), "token");
    }
    return args.urlFormatter.get().getWebUrl().orElse("") + "#/VE/" + emailToken;
  }
}
