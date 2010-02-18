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

package com.google.gerrit.server.auth.ldap;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GenericCurrentUserProvider;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;

import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

@Singleton
class LdapBindRealm extends LdapRealm {
  private final GenericCurrentUserProvider userProvider;
  private final Cache<Account.Id, CachedCredentials> passwordCache;

  @Inject
  LdapBindRealm(
      final AuthConfig authConfig,
      final GroupCache groupCache,
      final EmailExpander emailExpander,
      final SchemaFactory<ReviewDb> schema,
      final GenericCurrentUserProvider userProvider,
      @Named(LdapModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.Id>> rawGroup,
      @Named(LdapModule.USERNAME_CACHE) final Cache<String, Account.Id> rawUsername,
      @Named(LdapBindModule.PASSWORD_CACHE) final Cache<Account.Id, CachedCredentials> rawPassword,
      @GerritServerConfig final Config config) {
    super(authConfig, groupCache, emailExpander, schema, rawGroup, rawUsername,
        config);
    this.passwordCache = rawPassword;
    this.userProvider = userProvider;
  }

  @Override
  public void onLogin(AuthRequest who, Account.Id accountId) {
    passwordCache.put(accountId, new CachedCredentials(who));
    super.onLogin(who, accountId);
  }

  @Override
  public void onLogout(Account.Id accountId) {
    passwordCache.remove(accountId);
    super.onLogout(accountId);
  }

  @Override
  protected DirContext open() throws NamingException {
    CurrentUser who;
    try {
      who = userProvider.get();
    } catch (OutOfScopeException err) {
      who = null;
    }

    if (who instanceof IdentifiedUser) {
      CachedCredentials c;

      c = passwordCache.get(((IdentifiedUser) who).getAccountId());
      if (c != null) {
        try {
          return authenticate(c.username, c.password);
        } catch (AccountException why) {
          NamingException error;

          error = new NamingException("Cannot login with cached password");
          error.initCause(why);
          throw error;
        }
      }
    }

    throw new NamingException("No user to authenticate to directory with");
  }
}
