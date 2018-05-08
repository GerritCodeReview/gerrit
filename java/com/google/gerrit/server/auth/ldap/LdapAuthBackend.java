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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthRequest;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.InvalidCredentialsException;
import com.google.gerrit.server.auth.MissingCredentialsException;
import com.google.gerrit.server.auth.UnknownUserException;
import com.google.gerrit.server.auth.UserNotAllowedException;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Locale;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.login.LoginException;
import org.eclipse.jgit.lib.Config;

/** Implementation of AuthBackend for the LDAP authentication system. */
public class LdapAuthBackend implements AuthBackend {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Helper helper;
  private final AuthConfig authConfig;
  private final boolean lowerCaseUsername;

  @Inject
  public LdapAuthBackend(Helper helper, AuthConfig authConfig, @GerritServerConfig Config config) {
    this.helper = helper;
    this.authConfig = authConfig;
    this.lowerCaseUsername = config.getBoolean("ldap", "localUsernameToLowerCase", false);
  }

  @Override
  public String getDomain() {
    return "ldap";
  }

  @Override
  public AuthUser authenticate(AuthRequest req)
      throws MissingCredentialsException, InvalidCredentialsException, UnknownUserException,
          UserNotAllowedException, AuthException {
    if (!req.getUsername().isPresent() || !req.getPassword().isPresent()) {
      throw new MissingCredentialsException();
    }

    String username =
        lowerCaseUsername
            ? req.getUsername().map(u -> u.toLowerCase(Locale.US)).get()
            : req.getUsername().get();
    try {
      final DirContext ctx;
      if (authConfig.getAuthType() == AuthType.LDAP_BIND) {
        ctx = helper.authenticate(username, req.getPassword().get());
      } else {
        ctx = helper.open();
      }
      try {
        final Helper.LdapSchema schema = helper.getSchema(ctx);
        final LdapQuery.Result m = helper.findAccount(schema, ctx, username, false);

        if (authConfig.getAuthType() == AuthType.LDAP) {
          // We found the user account, but we need to verify
          // the password matches it before we can continue.
          //
          helper.close(helper.authenticate(m.getDN(), req.getPassword().get()));
        }
        return new AuthUser(AuthUser.UUID.create(username), username);
      } finally {
        helper.close(ctx);
      }
    } catch (AccountException e) {
      logger.atSevere().withCause(e).log("Cannot query LDAP to authenticate user");
      throw new InvalidCredentialsException("Cannot query LDAP for account", e);
    } catch (IOException | NamingException e) {
      logger.atSevere().withCause(e).log("Cannot query LDAP to authenticate user");
      throw new AuthException("Cannot query LDAP for account", e);
    } catch (LoginException e) {
      logger.atSevere().withCause(e).log("Cannot authenticate server via JAAS");
      throw new AuthException("Cannot query LDAP for account", e);
    }
  }
}
