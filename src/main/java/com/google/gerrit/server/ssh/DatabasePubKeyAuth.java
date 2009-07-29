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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.AccountSshKey;
import com.google.gerrit.client.reviewdb.ReviewDb;
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
  private final SshKeyCache sshKeyCache;
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  DatabasePubKeyAuth(final SshKeyCache skc, final SchemaFactory<ReviewDb> sf) {
    sshKeyCache = skc;
    schema = sf;
  }

  public boolean hasKey(final String username, final PublicKey inkey,
      final ServerSession session) {
    SshKeyCacheEntry matched = null;

    for (final SshKeyCacheEntry k : sshKeyCache.get(username)) {
      if (k.match(inkey)) {
        if (matched == null) {
          matched = k;

        } else if (!matched.getAccount().equals(k.getAccount())) {
          // Don't permit keys to authenticate to different accounts
          // that have the same username and public key.
          //
          // We'd have to pick one at random, yielding unpredictable
          // behavior for the end-user.
          //
          return false;
        }
      }
    }

    if (matched != null) {
      matched.updateLastUsed(schema);
      session.setAttribute(SshUtil.CURRENT_ACCOUNT, matched.getAccount());
      return true;
    }
    return false;
  }
}
