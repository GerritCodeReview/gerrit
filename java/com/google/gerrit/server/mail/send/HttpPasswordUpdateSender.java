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

import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

/** Sender that informs a user by email that the HTTP password of their account was updated. */
public class HttpPasswordUpdateSender extends OutgoingEmail {
  public interface Factory {
    HttpPasswordUpdateSender create(IdentifiedUser user, String operation);
  }

  private final IdentifiedUser user;
  private final String operation;

  @AssistedInject
  public HttpPasswordUpdateSender(
      EmailArguments args, @Assisted IdentifiedUser user, @Assisted String operation) {
    super(args, "HttpPasswordUpdate");
    this.user = user;
    this.operation = operation;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", "[Gerrit Code Review] HTTP password was " + operation);
    String email = getEmail();
    if (email != null) {
      add(RecipientType.TO, Address.create(email));
    }
  }

  @Override
  protected boolean shouldSendMessage() {
    // Always send an email if the HTTP password is updated.
    return true;
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("HttpPasswordUpdate"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("HttpPasswordUpdateHtml"));
    }
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("userNameEmail", getUserNameEmailFor(user.getAccountId()));
    soyContextEmailData.put("operation", operation);
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }

  private String getEmail() {
    return user.getAccount().preferredEmail();
  }
}
