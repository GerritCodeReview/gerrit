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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class RegisterNewEmailSender extends OutgoingEmail {
  public interface Factory {
    public RegisterNewEmailSender create(String address);
  }

  private final EmailTokenVerifier tokenVerifier;
  private final IdentifiedUser user;
  private final String addr;
  private String emailToken;

  @Inject
  public RegisterNewEmailSender(EmailArguments ea,
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
  protected boolean shouldSendMessage() {
    return true;
  }

  @Override
  protected void format() throws EmailException {
    appendText(velocifyFile("RegisterNewEmail.vm"));
  }

  public String getUserNameEmail() {
    String name = user.getAccount().getFullName();
    String email = user.getAccount().getPreferredEmail();

    if (name != null && email != null) {
      return name + " <" + email + ">";
    } else if (email != null) {
      return email;
    } else if (name != null) {
      return name;
    } else {
      String username = user.getUserName();
      if (username != null) {
        return username;
      }
    }
    return null;
  }

  public String getEmailRegistrationToken() {
    if (emailToken == null) {
      emailToken = tokenVerifier.encode(user.getAccountId(), addr);
    }
    return emailToken;
  }
}
