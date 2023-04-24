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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.base.Joiner;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.mail.send.OutgoingEmailNew.EmailDecorator;
import java.util.Collections;
import java.util.List;

/** Informs a user by email about the removal of an SSH or GPG key from their account. */
@AutoFactory
public class DeleteKeyEmailDecorator implements EmailDecorator {
  private OutgoingEmailNew email;

  private final IdentifiedUser user;
  private final AccountSshKey sshKey;
  private final List<String> gpgKeyFingerprints;
  private final MessageIdGenerator messageIdGenerator;

  public DeleteKeyEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, IdentifiedUser user, AccountSshKey sshKey) {
    this.messageIdGenerator = messageIdGenerator;
    this.user = user;
    this.gpgKeyFingerprints = Collections.emptyList();
    this.sshKey = sshKey;
  }

  public DeleteKeyEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator,
      IdentifiedUser user,
      List<String> gpgKeyFingerprints) {
    this.messageIdGenerator = messageIdGenerator;
    this.user = user;
    this.gpgKeyFingerprints = gpgKeyFingerprints;
    this.sshKey = null;
  }

  @Override
  public void init(OutgoingEmailNew email) {
    this.email = email;

    email.setHeader("Subject", String.format("[Gerrit Code Review] %s Keys Deleted", getKeyType()));
    email.setMessageId(messageIdGenerator.fromAccountUpdate(user.getAccountId()));
    email.addByAccountId(RecipientType.TO, user.getAccountId());
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("email", getEmail());
    email.addSoyEmailDataParam("gpgKeyFingerprints", getGpgKeyFingerprints());
    email.addSoyEmailDataParam("keyType", getKeyType());
    email.addSoyEmailDataParam("sshKey", getSshKey());
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(user.getAccountId()));

    email.appendText(email.textTemplate("DeleteKey"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("DeleteKeyHtml"));
    }
  }

  private String getEmail() {
    return user.getAccount().preferredEmail();
  }

  private String getKeyType() {
    if (sshKey != null) {
      return "SSH";
    } else if (gpgKeyFingerprints != null) {
      return "GPG";
    }
    throw new IllegalStateException("key type is not SSH or GPG");
  }

  @Nullable
  private String getSshKey() {
    return (sshKey != null) ? sshKey.sshPublicKey() + "\n" : null;
  }

  @Nullable
  private String getGpgKeyFingerprints() {
    if (!gpgKeyFingerprints.isEmpty()) {
      return Joiner.on("\n").join(gpgKeyFingerprints);
    }
    return null;
  }
}
