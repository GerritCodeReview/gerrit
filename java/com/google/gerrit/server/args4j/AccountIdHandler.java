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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gwtorm.server.OrmException;
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

  @Inject
  public AccountIdHandler(
      AccountResolver accountResolver,
      @Assisted CmdLineParser parser,
      @Assisted OptionDef option,
      @Assisted Setter<Account.Id> setter) {
    super(parser, option, setter);
    this.accountResolver = accountResolver;
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String token = params.getParameter(0);
    Account.Id accountId;
    try {
      Account a = accountResolver.find(token);
      if (a != null) {
        accountId = a.getId();
      } else {
        throw new CmdLineException(owner, localizable("user \"%s\" not found"), token);
      }
    } catch (OrmException e) {
      throw new CmdLineException(owner, localizable("database is down"));
    } catch (IOException e) {
      throw new CmdLineException(owner, "Failed to load account", e);
    } catch (ConfigInvalidException e) {
      throw new CmdLineException(owner, "Invalid account config", e);
    }
    setter.addValue(accountId);
    return 1;
  }

  @Override
  public final String getDefaultMetaVariable() {
    return "EMAIL";
  }
}
