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

import static com.google.gerrit.sshd.SshUtil.AUTH_ATTEMPTED_AS;
import static com.google.gerrit.sshd.SshUtil.AUTH_ERROR;
import static com.google.gerrit.sshd.SshUtil.CURRENT_ACCOUNT;

import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.sshd.SshScopes.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
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
  private final SshLog log;

  @Inject
  DatabasePubKeyAuth(final SshKeyCacheImpl skc, final SshLog l) {
    sshKeyCache = skc;
    log = l;
  }

  public boolean authenticate(final String username,
      final PublicKey suppliedKey, final ServerSession session) {
    final Iterable<SshKeyCacheEntry> keyList = sshKeyCache.get(username);
    final SshKeyCacheEntry key = find(keyList, suppliedKey);
    if (key == null) {
      final String err;
      if (keyList == SshKeyCacheImpl.NO_SUCH_USER) {
        err = "user-not-found";
      } else if (keyList == SshKeyCacheImpl.NO_KEYS) {
        err = "key-list-empty";
      } else {
        err = "no-matching-key";
      }
      return fail(username, session, err);
    }

    // Double check that all of the keys are for the same user account.
    // This should have been true when the cache factory method loaded
    // the list into memory, but we want to be extra paranoid about our
    // security check to ensure there aren't two users sharing the same
    // user name on the server.
    //
    for (final SshKeyCacheEntry otherKey : keyList) {
      if (!key.getAccount().equals(otherKey.getAccount())) {
        return fail(username, session, "keys-cross-accounts");
      }
    }

    if (session.setAttribute(CURRENT_ACCOUNT, key.getAccount()) == null) {
      // If this is the first time we've authenticated this
      // session, record a login event in the log and add
      // a close listener to record a logout event.
      //
      final Context ctx = new Context(session);
      final Context old = SshScopes.current.get();
      try {
        SshScopes.current.set(ctx);
        log.onLogin();
      } finally {
        SshScopes.current.set(old);
      }

      session.getIoSession().getCloseFuture().addListener(
          new IoFutureListener<IoFuture>() {
            @Override
            public void operationComplete(IoFuture future) {
              final Context old = SshScopes.current.get();
              try {
                SshScopes.current.set(ctx);
                log.onLogout();
              } finally {
                SshScopes.current.set(old);
              }
            }
          });
    }

    return true;
  }

  private static boolean fail(final String username,
      final ServerSession session, final String err) {
    session.setAttribute(AUTH_ATTEMPTED_AS, username);
    session.setAttribute(AUTH_ERROR, err);
    return false;
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
