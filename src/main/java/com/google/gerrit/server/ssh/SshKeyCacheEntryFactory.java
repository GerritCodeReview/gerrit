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

import net.sf.ehcache.constructs.blocking.CacheEntryFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SshKeyCacheEntryFactory implements CacheEntryFactory {
  private final Logger log = LoggerFactory.getLogger(getClass());

  public Object createEntry(final Object genericKey) throws Exception {
    final String username = (String) genericKey;
    final ReviewDb db = Common.getSchemaFactory().open();
    try {
      final List<Account> matches =
          db.accounts().bySshUserName(username).toList();
      if (matches.isEmpty()) {
        return Collections.<SshKeyCacheEntry> emptyList();
      }

      final List<SshKeyCacheEntry> kl = new ArrayList<SshKeyCacheEntry>(4);
      for (final Account a : matches) {
        for (final AccountSshKey k : db.accountSshKeys().valid(a.getId())) {
          add(db, kl, k);
        }
      }
      return Collections.unmodifiableList(kl);
    } finally {
      db.close();
    }
  }

  private void add(ReviewDb db, List<SshKeyCacheEntry> kl, AccountSshKey k) {
    try {
      kl.add(new SshKeyCacheEntry(k.getKey(), SshUtil.parse(k)));
    } catch (OutOfMemoryError e) {
      // This is the only case where we assume the problem has nothing
      // to do with the key object, and instead we must abort this load.
      //
      throw e;
    } catch (Throwable e) {
      markInvalid(db, k);
    }
  }

  private void markInvalid(final ReviewDb db, final AccountSshKey k) {
    try {
      log.info("Flagging SSH key " + k.getKey() + " invalid");
      k.setInvalid();
      db.accountSshKeys().update(Collections.singleton(k));
    } catch (OrmException e) {
      log.error("Failed to mark SSH key" + k.getKey() + " invalid", e);
    }
  }
}
