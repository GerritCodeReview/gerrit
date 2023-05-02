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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Joiner;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import java.util.List;

/** Informs a user by email about the addition of an SSH or GPG key to their account. */
@AutoFactory
public class AddKeyEmailDecorator implements EmailDecorator {
  private OutgoingEmail email;

  private final IdentifiedUser user;
  private final AccountSshKey sshKey;
  private final List<String> gpgKeys;
  private final MessageIdGenerator messageIdGenerator;

  public AddKeyEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, IdentifiedUser user, AccountSshKey sshKey) {
    this.messageIdGenerator = messageIdGenerator;
    this.user = user;
    this.sshKey = sshKey;
    this.gpgKeys = null;
  }

  public AddKeyEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, IdentifiedUser user, List<String> gpgKeys) {
    this.messageIdGenerator = messageIdGenerator;
    this.user = user;
    this.sshKey = null;
    this.gpgKeys = gpgKeys;
  }

  @Override
  public void init(OutgoingEmail email) {
    this.email = email;

    email.setHeader(
        "Subject", String.format("[Gerrit Code Review] New %s Keys Added", getKeyType()));
    email.setMessageId(messageIdGenerator.fromAccountUpdate(user.getAccountId()));
    email.addByAccountId(RecipientType.TO, user.getAccountId());
  }

  @Override
  public boolean shouldSendMessage() {
    if (sshKey == null && (gpgKeys == null || gpgKeys.isEmpty())) {
      // Don't email if no keys were added.
      return false;
    }

    return true;
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("email", getEmail());
    email.addSoyEmailDataParam("gpgKeys", getGpgKeys());
    email.addSoyEmailDataParam("keyType", getKeyType());
    email.addSoyEmailDataParam("sshKey", getSshKey());
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(user.getAccountId()));
    email.addSoyEmailDataParam("sshKeysSettingsUrl", email.getSettingsUrl("ssh-keys"));
    email.addSoyEmailDataParam("gpgKeysSettingsUrl", email.getSettingsUrl("gpg-keys"));

    email.appendText(email.textTemplate("AddKey"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("AddKeyHtml"));
    }
  }

  private String getEmail() {
    return user.getAccount().preferredEmail();
  }

  private String getKeyType() {
    if (sshKey != null) {
      return "SSH";
    } else if (gpgKeys != null) {
      return "GPG";
    }
    return "Unknown";
  }

  @Nullable
  private String getSshKey() {
    return (sshKey != null) ? sshKey.sshPublicKey() + "\n" : null;
  }

  @Nullable
  private String getGpgKeys() {
    if (gpgKeys != null) {
      return Joiner.on("\n").join(gpgKeys);
    }
    return null;
  }
}
