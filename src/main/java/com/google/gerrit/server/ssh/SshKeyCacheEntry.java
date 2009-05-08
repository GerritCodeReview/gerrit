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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Collections;

class SshKeyCacheEntry {
  private static final Logger log =
      LoggerFactory.getLogger(SshKeyCacheEntry.class);

  private final AccountSshKey.Id id;
  private final PublicKey publicKey;

  SshKeyCacheEntry(final AccountSshKey.Id i, final PublicKey k) {
    id = i;
    publicKey = k;
  }

  Account.Id getAccount() {
    return id.getParentKey();
  }

  boolean match(final PublicKey inkey) {
    return publicKey.equals(inkey);
  }

  void updateLastUsed() {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        final AccountSshKey k = db.accountSshKeys().get(id);
        if (k != null) {
          k.setLastUsedOn();
          db.accountSshKeys().update(Collections.singleton(k));
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      log.warn("Failed to update \"" + id + "\" SSH key used", e);
    }
  }
}
