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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

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
  public AccountIdHandler(final AccountResolver accountResolver,
      final AccountManager accountManager,
      final AuthConfig authConfig,
      @Assisted final CmdLineParser parser, @Assisted final OptionDef option,
      @Assisted final Setter<Account.Id> setter) {
    super(parser, option, setter);
    this.accountResolver = accountResolver;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
  }

  @Override
  public final int parseArguments(final Parameters params)
      throws CmdLineException {
    final String token = params.getParameter(0);
    final Account.Id accountId;
    try {
      final Account a = accountResolver.find(token);
      if (a != null) {
        accountId = a.getId();
      } else {
        switch (authType) {
          case HTTP_LDAP:
          case CLIENT_SSL_CERT_LDAP:
          case LDAP:
            accountId = createAccountByLdap(token);
            break;
          default:
            throw new CmdLineException(owner, "user \"" + token + "\" not found");
        }
      }
    } catch (OrmException e) {
      throw new CmdLineException(owner, "database is down");
    }
    setter.addValue(accountId);
    return 1;
  }

  private Account.Id createAccountByLdap(String user)
      throws CmdLineException {
    if (!user.matches(Account.USER_NAME_PATTERN)) {
      throw new CmdLineException(owner, "user \"" + user + "\" not found");
    }

    try {
      AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountManager.authenticate(req).getAccountId();
    } catch (AccountException e) {
      throw new CmdLineException(owner, "user \"" + user + "\" not found");
    }
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "EMAIL";
  }
}
