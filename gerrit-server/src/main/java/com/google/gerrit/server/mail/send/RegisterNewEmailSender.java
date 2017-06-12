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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.EmailTokenVerifier;
import com.google.gerrit.server.mail.RecipientType;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class RegisterNewEmailSender extends OutgoingEmail {
  public interface Factory {
    RegisterNewEmailSender create(String address);
  }

  private final EmailTokenVerifier tokenVerifier;
  private final IdentifiedUser user;
  private final String addr;
  private String emailToken;

  @Inject
  public RegisterNewEmailSender(
      EmailArguments ea,
      EmailTokenVerifier etv,
      IdentifiedUser callingUser,
      @Assisted final String address) {
    super(ea, "registernewemail");
    tokenVerifier = etv;
    user = callingUser;
    addr = address;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] Email Verification");
    add(RecipientType.TO, new Address(addr));
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("RegisterNewEmail"));
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getEmailRegistrationToken() {
    if (emailToken == null) {
      emailToken = checkNotNull(tokenVerifier.encode(user.getAccountId(), addr), "token");
    }
    return emailToken;
  }

  public boolean isAllowed() {
    return args.emailSender.canEmail(addr);
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("emailRegistrationToken", getEmailRegistrationToken());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }
}
