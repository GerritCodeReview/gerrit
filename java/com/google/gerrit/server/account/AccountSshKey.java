// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.gerrit.reviewdb.client.Account;
import java.util.List;

/** An SSH key approved for use by an {@link Account}. */
@AutoValue
public abstract class AccountSshKey {
  public static AccountSshKey create(Account.Id accountId, int seq, String sshPublicKey) {
    return create(accountId, seq, sshPublicKey, true);
  }

  public static AccountSshKey createInvalid(Account.Id accountId, int seq, String sshPublicKey) {
    return create(accountId, seq, sshPublicKey, false);
  }

  public static AccountSshKey createInvalid(AccountSshKey key) {
    return create(key.accountId(), key.seq(), key.sshPublicKey(), false);
  }

  public static AccountSshKey create(
      Account.Id accountId, int seq, String sshPublicKey, boolean valid) {
    return new AutoValue_AccountSshKey.Builder()
        .setAccountId(accountId)
        .setSeq(seq)
        .setSshPublicKey(stripOffNewLines(sshPublicKey))
        .setValid(valid && seq > 0)
        .build();
  }

  private static String stripOffNewLines(String s) {
    return s.replace("\n", "").replace("\r", "");
  }

  public abstract Account.Id accountId();

  public abstract int seq();

  public abstract String sshPublicKey();

  public abstract boolean valid();

  private String publicKeyPart(int index, String defaultValue) {
    String s = sshPublicKey();
    if (s != null && s.length() > 0) {
      List<String> parts = Splitter.on(' ').splitToList(s);
      if (parts.size() > index) {
        return parts.get(index);
      }
    }
    return defaultValue;
  }

  public String algorithm() {
    return publicKeyPart(0, "none");
  }

  public String encodedKey() {
    return publicKeyPart(1, null);
  }

  public String comment() {
    return publicKeyPart(2, "");
  }

  @AutoValue.Builder
  abstract static class Builder {
    public abstract Builder setAccountId(Account.Id accountId);

    public abstract Builder setSeq(int seq);

    public abstract Builder setSshPublicKey(String sshPublicKey);

    public abstract Builder setValid(boolean valid);

    public abstract AccountSshKey build();
  }
}
