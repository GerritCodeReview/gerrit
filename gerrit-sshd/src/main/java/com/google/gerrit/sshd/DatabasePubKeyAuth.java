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
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PeerDaemonUser;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.net.SocketAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Authenticates by public key through {@link AccountSshKey} entities.
 */
@Singleton
class DatabasePubKeyAuth implements PublickeyAuthenticator {
  private final SshKeyCacheImpl sshKeyCache;
  private final SshLog log;
  private final IdentifiedUser.GenericFactory userFactory;
  private final PeerDaemonUser.Factory peerFactory;
  private final Set<PublicKey> myHostKeys;

  @Inject
  DatabasePubKeyAuth(final SshKeyCacheImpl skc, final SshLog l,
      final IdentifiedUser.GenericFactory uf, final PeerDaemonUser.Factory pf,
      final KeyPairProvider hostKeyProvider) {
    sshKeyCache = skc;
    log = l;
    userFactory = uf;
    peerFactory = pf;
    myHostKeys = myHostKeys(hostKeyProvider);
  }

  private static Set<PublicKey> myHostKeys(KeyPairProvider p) {
    final Set<PublicKey> keys = new HashSet<PublicKey>(2);
    addPublicKey(keys, p, KeyPairProvider.SSH_RSA);
    addPublicKey(keys, p, KeyPairProvider.SSH_DSS);
    return keys;
  }

  private static void addPublicKey(final Collection<PublicKey> out,
      final KeyPairProvider p, final String type) {
    final KeyPair pair = p.loadKey(type);
    if (pair != null && pair.getPublic() != null) {
      out.add(pair.getPublic());
    }
  }

  public boolean authenticate(final String username,
      final PublicKey suppliedKey, final ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    if (PeerDaemonUser.USER_NAME.equals(username)) {
      if (myHostKeys.contains(suppliedKey)) {
        PeerDaemonUser user = peerFactory.create(sd.getRemoteAddress());
        return success(username, session, sd, user);

      } else {
        sd.authenticationError(username, "no-matching-key");
        return false;
      }
    }

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
      sd.authenticationError(username, err);
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
        sd.authenticationError(username, "keys-cross-accounts");
        return false;
      }
    }

    return success(username, session, sd, createUser(sd, key));
  }

  private boolean success(final String username, final ServerSession session,
      final SshSession sd, final CurrentUser user) {
    if (sd.getCurrentUser() == null) {
      sd.authenticationSuccess(username, user);

      // If this is the first time we've authenticated this
      // session, record a login event in the log and add
      // a close listener to record a logout event.
      //
      Context ctx = new Context(sd);
      Context old = SshScope.set(ctx);
      try {
        log.onLogin();
      } finally {
        SshScope.set(old);
      }

      session.getIoSession().getCloseFuture().addListener(
          new IoFutureListener<IoFuture>() {
            @Override
            public void operationComplete(IoFuture future) {
              final Context ctx = new Context(sd);
              final Context old = SshScope.set(ctx);
              try {
                log.onLogout();
              } finally {
                SshScope.set(old);
              }
            }
          });
    }

    return true;
  }

  private IdentifiedUser createUser(final SshSession sd,
      final SshKeyCacheEntry key) {
    return userFactory.create(AccessPath.SSH, new Provider<SocketAddress>() {
      @Override
      public SocketAddress get() {
        return sd.getRemoteAddress();
      }
    }, key.getAccount());
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
