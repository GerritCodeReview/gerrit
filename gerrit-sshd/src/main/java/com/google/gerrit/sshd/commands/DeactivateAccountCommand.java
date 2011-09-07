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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountSshKey;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;

import java.util.Collections;

/** Deactivates a user account.Based on create account command **/
final class DeactivateAccountCommand extends BaseCommand {

  @Argument(index = 0, required = true, metaVar = "USERNAME", usage = "name of the user account")
  private String username;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        // only administrators can deactivate accounts
        if (!currentUser.getCapabilities().canAdministrateServer()) {
          String msg =
              String.format(
                  "fatal: %s does not have \"Administrator\" capability.",
                  currentUser.getUserName());
          throw new UnloggedFailure(1, msg);
        }

        parseCommandLine();
        deactivateAccount();
      }
    });
  }

  /**
   * Deactivates a given user account by removing the SSH Keys and setting its
   * active flag to false.
   *
   * @throws OrmException
   * @throws UnloggedFailure
   */
  private void deactivateAccount() throws UnloggedFailure, OrmException {

    if (!username.matches(Account.USER_NAME_PATTERN)) {
      throw die("Username '" + username + "'"
          + " must contain only letters, numbers, _, - or .");
    }

    final Account account;

    AccountExternalId.Key key =
        new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, username);
    AccountExternalId accExtId = db.accountExternalIds().get(key);

    if (accExtId != null) {
      account = db.accounts().get(accExtId.getAccountId());
    } else {
      throw die("Could not find user : " + username);
    }

    ResultSet<AccountSshKey> keys =
        db.accountSshKeys().byAccount(account.getId());
    db.accountSshKeys().delete(keys);
    account.setActive(false);
    db.accounts().update(Collections.singleton(account));
  }

}
