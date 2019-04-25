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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.Address;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Set;

/**
 * This class will be used to inform the user's prefered email of any changes to their email, e.g
 * adding a new email.
 */
public class EmailModifiedAddressSender extends OutgoingEmail {
  public interface Factory {
    EmailModifiedAddressSender create(
        @Assisted("address") String address,
        IdentifiedUser user,
        @Assisted("operation") String operation);
  }

  private final String addr;
  private final IdentifiedUser user;
  private final String operation;

  @Inject
  public EmailModifiedAddressSender(
      EmailArguments ea,
      @Assisted("address") String address,
      @Assisted IdentifiedUser user,
      @Assisted("operation") String operation) {
    super(ea, "emailmodifiedaddress");
    this.addr = address;
    this.user = user;
    this.operation = operation;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] Email Changes to your account");

    List<String> emails = getEmails();
    if (!emails.isEmpty()) {
      addByEmail(RecipientType.TO, emails.stream().map(e -> new Address(e)).collect(toList()));
    }
  }

  public List<String> getEmails() {
    if (user.getAccount().getPreferredEmail() != null) {
      return ImmutableList.of(user.getAccount().getPreferredEmail());
    }
    Set<String> emails = user.getEmailAddresses();
    if (!emails.isEmpty()) {
      return ImmutableList.copyOf(emails);
    }
    return ImmutableList.of();
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("EmailModifiedAddress"));
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getOperation() {
    return operation;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("address", addr);
    soyContextEmailData.put("email", getEmails());
    soyContextEmailData.put("operation", getOperation());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }
}
