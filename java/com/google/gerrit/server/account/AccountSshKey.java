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

import com.google.common.base.Splitter;
import com.google.gerrit.entities.Account;
import java.util.List;

/** An SSH key approved for use by an {@link Account}. */
public record AccountSshKey(Account.Id accountId, int seq, String sshPublicKey, boolean valid) {
  public AccountSshKey {
    sshPublicKey = stripOffNewLines(sshPublicKey);
    valid = valid && seq > 0;
  }

  public AccountSshKey(Account.Id accountId, int seq, String sshPublicKey) {
    this(accountId, seq, sshPublicKey, true);
  }

  /** Returns a copy of this key that is flagged as invalid. */
  public AccountSshKey asInvalid() {
    return new AccountSshKey(accountId, seq, sshPublicKey, false);
  }

  private static String stripOffNewLines(String s) {
    return s.replace("\n", "").replace("\r", "");
  }

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
}
