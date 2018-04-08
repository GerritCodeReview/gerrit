// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Optional;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.Config;

/** Authenticates users with kerberos (gssapi-with-mic). */
@Singleton
class GerritGSSAuthenticator extends GSSAuthenticator {
  private final AccountCache accounts;
  private final SshScope sshScope;
  private final SshLog sshLog;
  private final GenericFactory userFactory;
  private final Config config;

  @Inject
  GerritGSSAuthenticator(
      AccountCache accounts,
      SshScope sshScope,
      SshLog sshLog,
      IdentifiedUser.GenericFactory userFactory,
      @GerritServerConfig Config config) {
    this.accounts = accounts;
    this.sshScope = sshScope;
    this.sshLog = sshLog;
    this.userFactory = userFactory;
    this.config = config;
  }

  @Override
  public boolean validateIdentity(ServerSession session, String identity) {
    SshSession sd = session.getAttribute(SshSession.KEY);
    int at = identity.indexOf('@');
    String username;
    if (at == -1) {
      username = identity;
    } else {
      username = identity.substring(0, at);
    }
    if (config.getBoolean("auth", "userNameToLowerCase", false)) {
      username = username.toLowerCase(Locale.US);
    }

    Optional<Account> account =
        accounts.getByUsername(username).map(AccountState::getAccount).filter(Account::isActive);
    if (!account.isPresent()) {
      return false;
    }

    return SshUtil.success(
        username,
        session,
        sshScope,
        sshLog,
        sd,
        SshUtil.createUser(sd, userFactory, account.get().getId()));
  }
}
