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

package com.google.gerrit.server.auth.ldap;

import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.PasswordCredentials;
import com.google.gerrit.server.auth.PasswordCredentialsVerifier;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.login.LoginException;

public class LdapPasswordCredentialsVerifier extends
    PasswordCredentialsVerifier {
  private static final Logger log = LoggerFactory
      .getLogger(LdapPasswordCredentialsVerifier.class);

  private final Helper helper;
  private final AuthType authType;
  private final boolean usernameToLowerCase;

  @Inject
  public LdapPasswordCredentialsVerifier(Helper helper,
      AuthConfig authConfig,
      @GerritServerConfig Config config) {
    this.helper = helper;
    this.authType = authConfig.getAuthType();
    this.usernameToLowerCase =
        config.getBoolean("ldap", "localUsernameToLowerCase", false);
  }

  @Override
  protected PasswordAuthUser lookup(PasswordCredentials creds)
      throws AuthException {
    final String username = normalizeUsername(creds);
    try {
      final DirContext ctx;
      if (authType == AuthType.LDAP_BIND) {
        ctx = helper.authenticate(username, creds.getPassword());
      } else {
        ctx = helper.open();
      }
      try {
        final Helper.LdapSchema schema = helper.getSchema(ctx);
        final LdapQuery.Result m = helper.findAccount(schema, ctx, username);

        if (authType == AuthType.LDAP) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          helper.authenticate(m.getDN(), creds.getPassword());
        }
        return new PasswordAuthUser(
            new AuthUser(new AuthUser.UUID(username), username),
            creds.getPassword());
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (AccountException e) {
      log.error("Cannot query LDAP to authenticate user", e);
      throw new InvalidCredentialsException("Cannot query LDAP for account", e);
    } catch (NamingException e) {
      log.error("Cannot query LDAP to authenticate user", e);
      throw new AuthException("Cannot query LDAP for account", e);
    } catch (LoginException e) {
      log.error("Cannot authenticate server via JAAS", e);
      throw new AuthException("Cannot query LDAP for account", e);
    }
  }

  private String normalizeUsername(PasswordCredentials creds) {
    return usernameToLowerCase
        ? creds.getUsername().toLowerCase(Locale.US)
        : creds.getUsername();
  }
}
