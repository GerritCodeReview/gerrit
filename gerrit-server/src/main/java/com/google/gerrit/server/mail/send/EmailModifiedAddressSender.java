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

import com.google.common.base.Joiner;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.Address;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * This class will be used to inform the user's prefered email of any changes to their email, e.g
 * adding a new email.
 */
public class EmailModifiedAddressSender extends OutgoingEmail {
  public interface Factory {
    EmailModifiedAddressSender create(String address, IdentifiedUser user, List<String> operation);
  }

  private final IdentifiedUser callingUser;
  private final String addr;
  private final IdentifiedUser user;
  private final List<String> operation;

  @Inject
  public EmailModifiedAddressSender(
      EmailArguments ea,
      IdentifiedUser callingUser,
      @Assisted String address,
      @Assisted IdentifiedUser user,
      @Assisted List<String> operation) {
    super(ea, "emailmodifiedaddress");
    this.callingUser = callingUser;
    this.addr = address;
    this.user = user;
    this.operation = operation;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] Email Changes to your account");

    addByEmail(RecipientType.TO, getEmail());
  }

  public Collection getEmail() {
    Collection<Address> address = new LinkedList<Address>();
    if (user.getAccount().getPreferredEmail() != null) {
      address.add(new Address(user.getAccount().getPreferredEmail()));
    } else {
      // If the user does not have a preferred email, send it
      // to one of their secondary emails (if they have one).
      for (String email : user.getEmailAddresses()) {
        address.add(new Address(email));
      }
    }
    return address;
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("EmailModifiedAddress"));
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getOperation() {
    if (operation != null) {
      return Joiner.on("").join(operation);
    }
    return null;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("address", addr);
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("operation", getOperation());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }
}
