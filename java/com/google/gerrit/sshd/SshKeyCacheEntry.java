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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import java.security.PublicKey;

class SshKeyCacheEntry {
  private final AccountSshKey.Id id;
  private final PublicKey publicKey;

  SshKeyCacheEntry(AccountSshKey.Id i, PublicKey k) {
    id = i;
    publicKey = k;
  }

  Account.Id getAccount() {
    return id.getParentKey();
  }

  boolean match(PublicKey inkey) {
    return publicKey.equals(inkey);
  }
}
