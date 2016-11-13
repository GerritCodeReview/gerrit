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

package com.google.gerrit.reviewdb.client;

import com.google.gwtorm.client.IntKey;
import java.util.Objects;

/** An SSH key approved for use by an {@link Account}. */
public final class AccountSshKey {
  public static class Id extends IntKey<Account.Id> {
    private static final long serialVersionUID = 1L;

    protected Account.Id accountId;

    protected int seq;

    protected Id() {
      accountId = new Account.Id();
    }

    public Id(final Account.Id a, final int s) {
      accountId = a;
      seq = s;
    }

    @Override
    public Account.Id getParentKey() {
      return accountId;
    }

    @Override
    public int get() {
      return seq;
    }

    @Override
    protected void set(int newValue) {
      seq = newValue;
    }

    public boolean isValid() {
      return seq > 0;
    }
  }

  protected AccountSshKey.Id id;

  protected String sshPublicKey;

  protected boolean valid;

  protected AccountSshKey() {}

  public AccountSshKey(final AccountSshKey.Id i, final String pub) {
    id = i;
    sshPublicKey = pub;
    valid = id.isValid();
  }

  public Account.Id getAccount() {
    return id.accountId;
  }

  public AccountSshKey.Id getKey() {
    return id;
  }

  public String getSshPublicKey() {
    return sshPublicKey;
  }

  private String getPublicKeyPart(int index, String defaultValue) {
    String s = getSshPublicKey();
    if (s != null && s.length() > 0) {
      String[] parts = s.split(" ");
      if (parts.length > index) {
        return parts[index];
      }
    }
    return defaultValue;
  }

  public String getAlgorithm() {
    return getPublicKeyPart(0, "none");
  }

  public String getEncodedKey() {
    return getPublicKeyPart(1, null);
  }

  public String getComment() {
    return getPublicKeyPart(2, "");
  }

  public boolean isValid() {
    return valid && id.isValid();
  }

  public void setInvalid() {
    valid = false;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof AccountSshKey) {
      AccountSshKey other = (AccountSshKey) o;
      return Objects.equals(id, other.id)
          && Objects.equals(sshPublicKey, other.sshPublicKey)
          && Objects.equals(valid, other.valid);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, sshPublicKey, valid);
  }
}
