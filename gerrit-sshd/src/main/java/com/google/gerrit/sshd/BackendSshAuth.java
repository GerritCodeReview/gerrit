// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.UniversalAuthBackend;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.gerrit.sshd.auth.SshAuthRequest;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;

/**
 * PublicKey authentication agaist one of the authentication plugins.
 */
@Singleton
public class BackendSshAuth implements PublickeyAuthenticator,
    PasswordAuthenticator {
  private static final Logger log = LoggerFactory
      .getLogger(BackendSshAuth.class);

  private final UniversalAuthBackend authBackend;
  private final AccountCache accounts;
  private final IdentifiedUser.GenericFactory userFactory;
  private final SshScope sshScope;
  private final SchemaFactory<ReviewDb> schema;

  private SshLog sshLog;


  @Inject
  public BackendSshAuth(final UniversalAuthBackend authBackend,
      final AccountCache accounts,
      final SchemaFactory<ReviewDb> schema,
      final IdentifiedUser.GenericFactory userFactory, final SshScope s,
      final SshLog l) {
    this.authBackend = authBackend;
    this.accounts = accounts;
    this.userFactory = userFactory;
    this.sshScope = s;
    this.sshLog = l;
    this.schema = schema;
  }

  @Override
  public boolean authenticate(String username, PublicKey key,
      ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    SshAuthRequest sshAuth =
        new SshAuthRequest(username, key, sd.getRemoteAddress());
    return authenticate(sd, sshAuth, session);
  }

  @Override
  public boolean authenticate(String username, String password,
      ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);
    SshAuthRequest sshAuth =
        new SshAuthRequest(username, password, sd.getRemoteAddress());
    return authenticate(sd, sshAuth, session);
  }


  private boolean authenticate(SshSession sd, SshAuthRequest sshAuth,
      ServerSession session) {
    try {
      AuthUser authUser = authBackend.authenticate(sshAuth);
      if (!createUser(sd, authUser).getAccount().isActive()) {
        sd.authenticationError(sshAuth.getUsername(), "inactive-account");
        return false;
      }

      return success(sshAuth.getUsername(), session, sd,
          createUser(sd, authUser));
    } catch (AuthException e) {
      log.warn("Authentication failed for " + sshAuth.getUsername(), e);
      return false;
    }
  }

  private IdentifiedUser createUser(final SshSession sd, final AuthUser user) {

    return userFactory.create(sd.getRemoteAddress(),
        accounts.getByUsername(user.getUsername()).getAccount().getId());
  }

  private boolean success(final String username, final ServerSession session,
      final SshSession sd, final CurrentUser user) {
    if (sd.getCurrentUser() == null) {
      sd.authenticationSuccess(username, user);

      // If this is the first time we've authenticated this
      // session, record a login event in the log and add
      // a close listener to record a logout event.
      //
      Context ctx = sshScope.newContext(schema, sd, null);
      Context old = sshScope.set(ctx);
      try {
        sshLog.onLogin();
      } finally {
        sshScope.set(old);
      }

      session.getIoSession().getCloseFuture()
          .addListener(new IoFutureListener<IoFuture>() {
            @Override
            public void operationComplete(IoFuture future) {
              final Context ctx = sshScope.newContext(schema, sd, null);
              final Context old = sshScope.set(ctx);
              try {
                sshLog.onLogout();
              } finally {
                sshScope.set(old);
              }
            }
          });
    }

    return true;
  }

}
