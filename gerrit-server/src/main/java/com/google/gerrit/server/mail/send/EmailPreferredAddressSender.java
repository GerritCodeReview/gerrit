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

import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.Address;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/**
 * This class will be used to inform the user's prefered email of any changes
 * to their email, e.g adding a new email.
 */
public class EmailPreferredAddressSender extends OutgoingEmail {
  public interface Factory {
    EmailPreferredAddressSender create(IdentifiedUser user, String operation, String address);
  }

  private final IdentifiedUser callingUser;
  private final IdentifiedUser user;
  private final String addr;
  private final String operation;

  @Inject
  public EmailPreferredAddressSender(
      EmailArguments ea,
      IdentifiedUser callingUser,
      @Assisted IdentifiedUser user,
      @Assisted("Operation") String operation,
      @Assisted("Address") String address) {
    super(ea, "emailpreferredaddress");
    this.callingUser = callingUser;
    this.user = user;
    this.operation = operation;
    this.addr = address;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] Email Changes to your account");
    add(RecipientType.TO, new Address(getEmail()));
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("EmailPreferredAddress"));
  }

  public String getEmail() {
    return user.getAccount().getPreferredEmail();
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public boolean isAllowed() {
    return args.emailSender.canEmail(addr);
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("address", addr);
    soyContextEmailData.put("operation", operation);
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }
}
