// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.net.SocketAddress;

/**
 * Authenticates by password through {@link AccountExternalId} entities.
 */
@Singleton
class DatabasePasswordAuth implements PasswordAuthenticator {
  private final AccountCache accountCache;
  private final SshLog log;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DatabasePasswordAuth(final AccountCache ac, final SshLog l,
      final IdentifiedUser.GenericFactory uf) {
    accountCache = ac;
    log = l;
    userFactory = uf;
  }

  @Override
  public boolean authenticate(final String username, final String password,
      final ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    AccountState state = accountCache.getByUsername(username);
    if (state == null) {
      sd.authenticationError(username, "user-not-found");
      return false;
    }

    final String p = state.getPassword(username);
    if (p == null) {
      sd.authenticationError(username, "no-password");
      return false;
    }

    if (!p.equals(password)) {
      sd.authenticationError(username, "incorrect-password");
      return false;
    }

    if (sd.getCurrentUser() == null) {
      sd.authenticationSuccess(username, createUser(sd, state));

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
      final AccountState state) {
    return userFactory.create(AccessPath.SSH, new Provider<SocketAddress>() {
      @Override
      public SocketAddress get() {
        return sd.getRemoteAddress();
      }
    }, state.getAccount().getId());
  }
}
