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

import static com.google.gerrit.sshd.SshUtil.AUTH_ATTEMPTED_AS;
import static com.google.gerrit.sshd.SshUtil.AUTH_ERROR;
import static com.google.gerrit.sshd.SshUtil.CURRENT_ACCOUNT;

import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.sshd.SshScopes.Context;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * Authenticates by password through {@link AccountExternalId} entities.
 */
@Singleton
class DatabasePasswordAuth implements PasswordAuthenticator {
  private final AccountCache accountCache;
  private final SshLog log;

  @Inject
  DatabasePasswordAuth(final AccountCache ac, final SshLog l) {
    accountCache = ac;
    log = l;
  }

  @Override
  public boolean authenticate(final String username, final String password,
      final ServerSession session) {
    AccountState state = accountCache.getByUsername(username);
    if (state == null) {
      return fail(username, session, "user-not-found");
    }

    final String p = state.getPassword(username);
    if (p == null) {
      return fail(username, session, "no-password");
    }

    if (!p.equals(password)) {
      return fail(username, session, "incorrect-password");
    }

    if (session.setAttribute(CURRENT_ACCOUNT, state.getAccount().getId()) == null) {
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
}
