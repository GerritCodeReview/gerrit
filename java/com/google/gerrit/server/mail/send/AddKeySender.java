// Copyright (C) 2015 The Android Open Source Project
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
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.List;

public class AddKeySender extends OutgoingEmail {
  public interface Factory {
    AddKeySender create(IdentifiedUser user, AccountSshKey sshKey);

    AddKeySender create(IdentifiedUser user, List<String> gpgKey);
  }

  private final IdentifiedUser user;
  private final AccountSshKey sshKey;
  private final List<String> gpgKeys;

  @AssistedInject
  public AddKeySender(
      EmailArguments args, @Assisted IdentifiedUser user, @Assisted AccountSshKey sshKey) {
    super(args, "addkey");
    this.user = user;
    this.sshKey = sshKey;
    this.gpgKeys = null;
  }

  @AssistedInject
  public AddKeySender(
      EmailArguments args, @Assisted IdentifiedUser user, @Assisted List<String> gpgKeys) {
    super(args, "addkey");
    this.user = user;
    this.sshKey = null;
    this.gpgKeys = gpgKeys;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject", String.format("[Gerrit Code Review] New %s Keys Added", getKeyType()));
    add(RecipientType.TO, Address.tryParse(getUserNameEmail()));
  }

  @Override
  protected boolean shouldSendMessage() {
    if (sshKey == null && (gpgKeys == null || gpgKeys.isEmpty())) {
      // Don't email if no keys were added.
      return false;
    }

    return true;
  }

  @Override
  protected void format() throws EmailException {
    appendText(textTemplate("AddKey"));
    if (useHtml()) {
      appendHtml(soyHtmlTemplate("AddKeyHtml"));
    }
  }

  public String getEmail() {
    return user.getAccount().preferredEmail();
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getKeyType() {
    if (sshKey != null) {
      return "SSH";
    } else if (gpgKeys != null) {
      return "GPG";
    }
    return "Unknown";
  }

  public String getSshKey() {
    return (sshKey != null) ? sshKey.sshPublicKey() + "\n" : null;
  }

  public String getGpgKeys() {
    if (gpgKeys != null) {
      return Joiner.on("\n").join(gpgKeys);
    }
    return null;
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("email", getEmail());
    soyContextEmailData.put("gpgKeys", getGpgKeys());
    soyContextEmailData.put("keyType", getKeyType());
    soyContextEmailData.put("sshKey", getSshKey());
    soyContextEmailData.put("userNameEmail", getUserNameEmail());
  }

  @Override
  protected boolean supportsHtml() {
    return true;
  }
}
