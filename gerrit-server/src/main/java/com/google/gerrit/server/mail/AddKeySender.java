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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.gpg.PublicKeyStore;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.bouncycastle.openpgp.PGPPublicKey;

public class AddKeySender extends OutgoingEmail {
  public interface Factory {
    public AddKeySender create(IdentifiedUser user,
        @Nullable AccountSshKey sshKey,
        @Nullable PGPPublicKey gpgKey);
  }

  private final IdentifiedUser callingUser;
  private final IdentifiedUser user;
  private final AccountSshKey sshKey;
  private final PGPPublicKey gpgKey;

  @Inject
  public AddKeySender(EmailArguments ea,
      IdentifiedUser callingUser,
      @Assisted final IdentifiedUser user,
      @Nullable @Assisted final AccountSshKey sshKey,
      @Nullable @Assisted final PGPPublicKey gpgKey) {
    super(ea, "addsshkey");
    this.callingUser = callingUser;
    this.user = user;
    this.sshKey = sshKey;
    this.gpgKey = gpgKey;
  }

  @Override
  protected void init() throws EmailException {
    super.init();
    setHeader("Subject",
        String.format("[Gerrit Code Review] %s Key Added", getKeyType()));
    add(RecipientType.TO, new Address(user.getAccount().getPreferredEmail()));
  }

  @Override
  protected boolean shouldSendMessage() {
    // Don't send an email if an admin is adding a key to a user.
    return !(!user.equals(callingUser) &&
        callingUser.getCapabilities().canAdministrateServer());
  }

  @Override
  protected void format() throws EmailException {
    appendText(velocifyFile("AddKey.vm"));
  }

  public String getUserNameEmail() {
    return getUserNameEmailFor(user.getAccountId());
  }

  public String getKeyType() {
    if (sshKey != null) {
      return "SSH";
    } else if (gpgKey != null) {
      return "GPG";
    }
    return "Unknown";
  }

  public String getSshKey() {
    return (sshKey != null) ? sshKey.getSshPublicKey() : null;
  }

  public String getGpgKey() {
    return (gpgKey != null) ? PublicKeyStore.keyToString(gpgKey) : null;
  }
}
