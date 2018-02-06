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
import com.google.gerrit.reviewdb.client.Account;
import java.io.Serializable;
import java.util.Objects;

/** An SSH key approved for use by an {@link Account}. */
@AutoValue
public abstract class AccountSshKey {
  public static class Id implements Serializable {
    private static final long serialVersionUID = 2L;

    private Account.Id accountId;
    private int seq;

    public Id(Account.Id a, int s) {
      accountId = a;
      seq = s;
    }

    public Account.Id getParentKey() {
      return accountId;
    }

    public int get() {
      return seq;
    }

    public boolean isValid() {
      return seq > 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(accountId, seq);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Id)) {
        return false;
      }
      Id otherId = (Id) obj;
      return Objects.equals(accountId, otherId.accountId) && Objects.equals(seq, otherId.seq);
    }
  }

  public static AccountSshKey create(AccountSshKey.Id id, String sshPublicKey) {
    return create(id, sshPublicKey, true);
  }

  public static AccountSshKey createInvalid(AccountSshKey.Id id, String sshPublicKey) {
    return create(id, sshPublicKey, false);
  }

  public static AccountSshKey createInvalid(AccountSshKey key) {
    return create(key.id(), key.sshPublicKey(), false);
  }

  public static AccountSshKey create(AccountSshKey.Id id, String sshPublicKey, boolean valid) {
    return new AutoValue_AccountSshKey.Builder()
        .setId(id)
        .setSshPublicKey(stripOffNewLines(sshPublicKey))
        .setValid(valid && id.isValid())
        .build();
  }

  private static String stripOffNewLines(String s) {
    return s.replace("\n", "").replace("\r", "");
  }

  public abstract AccountSshKey.Id id();

  public abstract String sshPublicKey();

  public abstract boolean valid();

  public Account.Id account() {
    return id().accountId;
  }

  private String publicKeyPart(int index, String defaultValue) {
    String s = sshPublicKey();
    if (s != null && s.length() > 0) {
      String[] parts = s.split(" ");
      if (parts.length > index) {
        return parts[index];
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

  //  public boolean isValid() {
  //    return valid && id.isValid();
  //  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(AccountSshKey.Id id);

    public abstract Builder setSshPublicKey(String sshPublicKey);

    public abstract Builder setValid(boolean valid);

    public abstract AccountSshKey build();
  }
}
