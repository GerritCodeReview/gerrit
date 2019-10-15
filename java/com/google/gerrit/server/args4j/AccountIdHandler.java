// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.args4j;

import static com.google.gerrit.util.cli.Localizable.localizable;

import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class AccountIdHandler extends OptionHandler<Account.Id> {
  private final AccountResolver accountResolver;
  private final AccountManager accountManager;
  private final AuthType authType;

  @Inject
  public AccountIdHandler(
      AccountResolver accountResolver,
      AccountManager accountManager,
      AuthConfig authConfig,
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<Account.Id> setter) {
    super(parser, option, setter);
    this.accountResolver = accountResolver;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String token = params.getParameter(0);
    Account.Id accountId;
    try {
      try {
        accountId = accountResolver.resolve(token).asUnique().account().id();
      } catch (UnprocessableEntityException e) {
        switch (authType) {
          case HTTP_LDAP:
          case CLIENT_SSL_CERT_LDAP:
          case LDAP:
            accountId = createAccountByLdap(token);
            break;
          case CUSTOM_EXTENSION:
          case DEVELOPMENT_BECOME_ANY_ACCOUNT:
          case HTTP:
          case LDAP_BIND:
          case OAUTH:
          case OPENID:
          case OPENID_SSO:
          default:
            throw new CmdLineException(owner, localizable("user \"%s\" not found"), token);
        }
      }
    } catch (StorageException e) {
      throw new CmdLineException(owner, localizable("database is down"));
    } catch (IOException e) {
      throw new CmdLineException(owner, "Failed to load account", e);
    } catch (ConfigInvalidException e) {
      throw new CmdLineException(owner, "Invalid account config", e);
    }
    setter.addValue(accountId);
    return 1;
  }

  private Account.Id createAccountByLdap(String user) throws CmdLineException, IOException {
    if (!ExternalId.isValidUsername(user)) {
      throw new CmdLineException(owner, localizable("user \"%s\" not found"), user);
    }

    try {
      AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountManager.authenticate(req).getAccountId();
    } catch (AccountException e) {
      throw new CmdLineException(owner, localizable("user \"%s\" not found"), user);
    }
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "EMAIL";
  }
}
