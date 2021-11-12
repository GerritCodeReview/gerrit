// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.gerrit.entities.Account;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

class SshKeyCacheEntry {
  private final Account.Id accountId;
  private final PublicKey publicKey;

  SshKeyCacheEntry(Account.Id accountId, PublicKey publicKey) {
    this.accountId = accountId;
    this.publicKey = publicKey;
  }

  Account.Id getAccount() {
    return accountId;
  }

  boolean match(PublicKey inkey) {
    // only verify the PublicKey interface, not any nested class objects
    return Objects.equals(publicKey.getAlgorithm(), inkey.getAlgorithm())
        && Objects.equals(publicKey.getFormat(), inkey.getFormat())
        && Arrays.equals(publicKey.getEncoded(), inkey.getEncoded());
  }
}
