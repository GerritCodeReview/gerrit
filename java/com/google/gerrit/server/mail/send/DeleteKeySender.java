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
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.Collections;
import java.util.List;

public class DeleteKeySender extends OutgoingEmail {
  public interface Factory {
    DeleteKeySender create(IdentifiedUser user, AccountSshKey sshKey);

    DeleteKeySender create(IdentifiedUser user, List<String> gpgKeyFingerprints);
  }

  private final PermissionBackend permissionBackend;
  private final IdentifiedUser callingUser;
  private final IdentifiedUser user;
  private final AccountSshKey sshKey;
  private final List<String> gpgKeyFingerprints;

  @AssistedInject
  public DeleteKeySender(
      EmailArguments ea,
      PermissionBackend permissionBackend,
      IdentifiedUser callingUser,
      @Assisted IdentifiedUser user,
      @Assisted AccountSshKey sshKey) {
    super(ea, "deletekey");
    this.permissionBackend = permissionBackend;
    this.callingUser = callingUser;
    this.user = user;
    this.gpgKeyFingerprints = Collections.emptyList();
    this.sshKey = sshKey;
  }

  @AssistedInject
  public DeleteKeySender(
      EmailArguments ea,
      PermissionBackend permissionBackend,
      IdentifiedUser callingUser,
      @Assisted IdentifiedUser user,
      @Assisted List<String> gpgKeyFingerprints) {
    super(ea, "deletekey");
    this.permissionBackend = permissionBackend;
    this.callingUser = callingUser;
    this.user = user;
    this.gpgKeyFingerprints = gpgKeyFingerprints;
    this.sshKey = null;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", String.format("[Gerrit Code Review] %s Keys Deleted", getKeyType()));
    add(RecipientType.TO, new Address(getEmail()));
  }

  @Override
  protected boolean shouldSendMessage() {
    if (user.equals(callingUser)) {
      // Send email if the user self-removed a key; this notification is necessary to alert
      // the user if their account was compromised and a key was unexpectedly deleted.
      return true;
    }

    try {
      // Don't email if an administrator removed a key on behalf of the user.
      permissionBackend.user(callingUser).check(GlobalPermission.ADMINISTRATE_SERVER);
      return false;
    } catch (AuthException | PermissionBackendException e) {
      // Send email if a non-administrator modified the keys, e.g. by MODIFY_ACCOUNT.
      return true;
    }
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("DeleteKey"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("DeleteKeyHtml"));
    }
  }

  public String getEmail() {
    return user.getAccount().getPreferredEmail();
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getKeyType() {
    if (sshKey != null) {
      return "SSH";
    } else if (gpgKeyFingerprints != null) {
      return "GPG";
    }
    throw new IllegalStateException("key type is not SSH or GPG");
  }

  public String getSshKey() {
    return (sshKey != null) ? sshKey.sshPublicKey() + "\n" : null;
  }

  public String getGpgKeyFingerprints() {
    if (!gpgKeyFingerprints.isEmpty()) {
      return Joiner.on("\n").join(gpgKeyFingerprints);
    }
    return null;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("gpgKeyFingerprints", getGpgKeyFingerprints());
    soyContextEmailData.put("keyType", getKeyType());
    soyContextEmailData.put("sshKey", getSshKey());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
