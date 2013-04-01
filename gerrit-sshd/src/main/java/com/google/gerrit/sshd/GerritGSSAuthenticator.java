// Copyright (C) 2013 Goldman Sachs
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.IdentifiedUser.GenericFactory;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * Authenticates users with kerberos (gssapi-with-mic).
 */
@Singleton
class GerritGSSAuthenticator extends GSSAuthenticator {
  private AccountCache accounts;
  private SshScope sshScope;
  private SshLog sshLog;
  private GenericFactory userFactory;

  @Inject
  GerritGSSAuthenticator(final AccountCache accounts, final SshScope sshScope,
      final SshLog sshLog, final IdentifiedUser.GenericFactory userFactory) {
    this.accounts = accounts;
    this.sshScope = sshScope;
    this.sshLog = sshLog;
    this.userFactory = userFactory;
  }

  @Override
  public boolean validateIdentity(final ServerSession session,
      final String identity) {
    final SshSession sd = session.getAttribute(SshSession.KEY);
    int at = identity.indexOf('@');
    String username;
    if(at == -1) {
      username = identity;
    } else {
      username = identity.substring(0, at);
    }
    AccountState state = accounts.getByUsername(username);
    Account account = state == null ? null : state.getAccount();
    boolean active = account != null && account.isActive();
    if(active) {
      return SshUtil.success(username, session, sshScope, sshLog, sd,
          SshUtil.createUser(sd, userFactory, account.getId()));
    } else {
      return false;
    }
  }
}
