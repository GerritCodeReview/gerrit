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
import com.google.gerrit.server.GerritServer;

import net.sf.ehcache.Element;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;

import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.Collections;

/**
 * Authenticates by public key through {@link AccountSshKey} entities.
 * <p>
 * The username supplied by the client must be the user's preferred email
 * address, as listed in their Account entity. Only keys listed under that
 * account as authorized keys are permitted to access the account.
 */
class DatabasePubKeyAuth implements PublickeyAuthenticator {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final SelfPopulatingCache sshKeysCache;

  DatabasePubKeyAuth(final GerritServer gs) {
    sshKeysCache = gs.getSshKeysCache();
  }

  public boolean hasKey(final String username, final PublicKey inkey,
      final ServerSession session) {
    SshKeyCacheEntry matched = null;

    for (final SshKeyCacheEntry k : get(username)) {
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
      matched.updateLastUsed();
      session.setAttribute(SshUtil.CURRENT_ACCOUNT, matched.getAccount());
      return true;
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private Iterable<SshKeyCacheEntry> get(final String username) {
    try {
      final Element e = sshKeysCache.get(username);
      if (e == null || e.getObjectValue() == null) {
        log.warn("Can't get SSH keys for \"" + username + "\" from cache.");
        return Collections.emptyList();
      }
      return (Iterable<SshKeyCacheEntry>) e.getObjectValue();
    } catch (RuntimeException e) {
      log.error("Can't get SSH keys for \"" + username + "\" from cache.", e);
      return Collections.emptyList();
    }
  }
}
