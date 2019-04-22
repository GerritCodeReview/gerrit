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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class HttpPasswordSender extends OutgoingEmail {
  public interface Factory {
    HttpPasswordSender create(IdentifiedUser user);
  }

  private final PermissionBackend permissionBackend;
  private final IdentifiedUser callingUser;
  private final IdentifiedUser user;

  @AssistedInject
  public HttpPasswordSender(
      EmailArguments ea,
      PermissionBackend permissionBackend,
      IdentifiedUser callingUser,
      @Assisted IdentifiedUser user) {
    super(ea, "HttpPasswordUpdate");
    this.permissionBackend = permissionBackend;
    this.callingUser = callingUser;
    this.user = user;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader(
        "Subject", String.format("[Gerrit Code Review] Http password was either changed or added"));
    add(RecipientType.TO, new Address(getEmail()));
  }

  @Override
  protected boolean shouldSendMessage() {
    // Always send a email if the HTTP password is added, changed or deleted.
    return true;
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("HttpPasswordUpdate"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("HttpPasswordUpdateHtml"));
    }
  }

  public String getEmail() {
    return user.getAccount().getPreferredEmail();
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
