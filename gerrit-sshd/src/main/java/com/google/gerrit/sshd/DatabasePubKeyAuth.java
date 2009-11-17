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

package com.google.gerrit.sshd;

import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.security.PublicKey;

/**
 * Authenticates by public key through {@link AccountSshKey} entities.
 * <p>
 * The username supplied by the client must be the user's preferred email
 * address, as listed in their Account entity. Only keys listed under that
 * account as authorized keys are permitted to access the account.
 */
@Singleton
class DatabasePubKeyAuth implements PublickeyAuthenticator {
  private final SshKeyCacheImpl sshKeyCache;
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  DatabasePubKeyAuth(final SshKeyCacheImpl skc, final SchemaFactory<ReviewDb> sf) {
    sshKeyCache = skc;
    schema = sf;
  }

  public boolean authenticate(final String username,
      final PublicKey suppliedKey, final ServerSession session) {
    final Iterable<SshKeyCacheEntry> keyList = sshKeyCache.get(username);
    final SshKeyCacheEntry key = find(keyList, suppliedKey);
    if (key == null) {
      return false;
    }

    // Double check that all of the keys are for the same user account.
    // This should have been true when the cache factory method loaded
    // the list into memory, but we want to be extra paranoid about our
    // security check to ensure there aren't two users sharing the same
    // user name on the server.
    //
    for (final SshKeyCacheEntry otherKey : keyList) {
      if (!key.getAccount().equals(otherKey.getAccount())) {
        return false;
      }
    }

    key.updateLastUsed(schema);
    session.setAttribute(SshUtil.CURRENT_ACCOUNT, key.getAccount());
    return true;
  }

  private SshKeyCacheEntry find(final Iterable<SshKeyCacheEntry> keyList,
      final PublicKey suppliedKey) {
    for (final SshKeyCacheEntry k : keyList) {
      if (k.match(suppliedKey)) {
        return k;
      }
    }
    return null;
  }
}
