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

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.UniversalAuthBackend;
import com.google.gerrit.sshd.auth.SshAuthRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.PublicKey;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PublicKey authentication agaist one of the authentication plugins. */
@Singleton
public class BackendSshAuth implements PublickeyAuthenticator, PasswordAuthenticator {
  private static final Logger log = LoggerFactory.getLogger(BackendSshAuth.class);

  private final UniversalAuthBackend authBackend;
  private final AccountCache accounts;
  private final IdentifiedUser.GenericFactory userFactory;
  private final SshScope sshScope;

  private SshLog sshLog;

  @Inject
  public BackendSshAuth(
      final UniversalAuthBackend authBackend,
      final AccountCache accounts,
      final IdentifiedUser.GenericFactory userFactory,
      final SshScope s,
      final SshLog l) {
    this.authBackend = authBackend;
    this.accounts = accounts;
    this.userFactory = userFactory;
    this.sshScope = s;
    this.sshLog = l;
  }

  @Override
  public boolean authenticate(String username, PublicKey key, ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);

    SshAuthRequest sshAuth = new SshAuthRequest(username, key, sd.getRemoteAddress());
    return authenticate(sd, sshAuth, session);
  }

  @Override
  public boolean authenticate(String username, String password, ServerSession session) {
    final SshSession sd = session.getAttribute(SshSession.KEY);
    SshAuthRequest sshAuth = new SshAuthRequest(username, password, sd.getRemoteAddress());
    return authenticate(sd, sshAuth, session);
  }

  private boolean authenticate(SshSession sd, SshAuthRequest sshAuth, ServerSession session) {
    try {
      if (!sshAuth.getUsername().isPresent()) {
        return false;
      }
      AuthUser authUser = authBackend.authenticate(sshAuth);
      String username = sshAuth.getUsername().get();
      if (!createUser(sd, authUser).getAccount().isActive()) {
        sd.authenticationError(username, "inactive-account");
        return false;
      }

      return SshUtil.success(username, session, sshScope, sshLog, sd, createUser(sd, authUser));
    } catch (AuthException e) {
      log.warn("Authentication failed for " + sshAuth.getUsername());
      return false;
    }
  }

  private IdentifiedUser createUser(final SshSession sd, final AuthUser user) {
    return userFactory.create(
        sd.getRemoteAddress(),
        accounts.getByUsername(user.getUsername()).get().getAccount().getId());
  }
}
