// Copyright (C) 2026 The Android Open Source Project
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
import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.account.AccountSshKey;
import com.google.gerrit.server.mail.send.OutgoingEmail.EmailDecorator;
import com.google.gerrit.server.util.time.TimeUtil;

/** Sender that informs a user by email that an SSH key will expire soon. */
@AutoFactory
public class SshKeyWillExpireEmailDecorator implements EmailDecorator {
  private OutgoingEmail email;

  private final Account account;
  private final AccountSshKey sshKey;
  private final MessageIdGenerator messageIdGenerator;

  public SshKeyWillExpireEmailDecorator(
      @Provided MessageIdGenerator messageIdGenerator, Account account, AccountSshKey sshKey) {
    this.messageIdGenerator = messageIdGenerator;
    this.account = account;
    this.sshKey = sshKey;
  }

  @Override
  public void init(OutgoingEmail email) {
    this.email = email;

    email.setHeader(
        "Subject",
        String.format("[Gerrit Code Review] SSH key '%s' will expire soon.", getKeyName()));
    email.setMessageId(
        messageIdGenerator.fromReasonAccountIdAndTimestamp(
            "Ssh_key_will_expire", account.id(), TimeUtil.now()));
    email.addByAccountId(RecipientType.TO, account.id());
  }

  @Override
  public void populateEmailContent() {
    email.addSoyEmailDataParam("email", account.preferredEmail());
    email.addSoyEmailDataParam("userNameEmail", email.getUserNameEmailFor(account.id()));
    email.addSoyEmailDataParam("keyName", getKeyName());
    email.addSoyEmailDataParam("sshPublicKey", sshKey.sshPublicKey());
    email.addSoyEmailDataParam("expirationDate", sshKey.expirationDate().get().toString());
    email.addSoyEmailDataParam("sshKeysSettingsUrl", email.getSettingsUrl("ssh-keys"));

    email.appendText(email.textTemplate("SshKeyWillExpire"));
    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("SshKeyWillExpireHtml"));
    }
  }

  private String getKeyName() {
    return Strings.isNullOrEmpty(sshKey.comment()) ? sshKey.algorithm() : sshKey.comment();
  }
}
