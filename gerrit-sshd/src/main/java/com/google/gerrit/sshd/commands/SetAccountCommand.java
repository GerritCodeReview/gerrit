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


import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;

/** Set a user's account settings. **/
final class SetAccountCommand extends BaseCommand {

  /*
   * set-account [--full-name <FULLNAME>] [--active|--inactive] [--add-email
   * <EMAIL>] [--delete-email <EMAIL> | ALL] [--add-ssh-key - | <KEY>]
   * [--delete-ssh-key - | <KEY> | ALL] <USERNAME>
   */
  @Argument(index = 0, required = true, metaVar = "USERNAME", usage = "name of the user account")
  private String username;

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--active", usage = "set account's state to active")
  private boolean active;

  @Option(name = "--inactive", usage = "set account's state to active")
  private boolean inactive;

  @Option(name = "--add-email", metaVar = "EMAIL", usage = "email address to add to the account")
  private String newEmail;

  @Option(name = "--delete-email", metaVar = "EMAIL", usage = "email address to delete from the account")
  private String oldEmail;

  @Option(name = "--add-ssh-key", metaVar = "-|KEY", usage = "public key to add to the account")
  private String addSshKey;

  @Option(name = "--delete-ssh-key", metaVar = "-|KEY", usage = "public key to delete from the account")
  private String deleteSshKey;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

  @Inject
  private AccountManager manager;

  @Inject
  private SshKeyCache sshKeyCache;

  @Inject
  private AccountCache byIdCache;

  @Inject
  private Realm realm;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canAdministrateServer()) {
          String msg =
              String.format(
                  "fatal: %s does not have \"Administrator\" capability.",
                  currentUser.getUserName());
          throw new UnloggedFailure(1, msg);
        }

        parseCommandLine();
        if (active && inactive) {
          throw new UnloggedFailure(1,
              "You can't use both --active and --inactive");
        }
        setAccount();
      }
    });
  }

  private void setAccount() throws OrmException, IOException,
      InvalidSshKeyException, UnloggedFailure {

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

    Account.Id id = account.getId();

    if (newEmail != null) {
      link(id, newEmail);
    }

    if (oldEmail != null) {
      deleteMail(id, oldEmail);
    }

    if (fullName != null && realm.allowsEdit(FieldName.FULL_NAME)) {
      account.setFullName(fullName);
    }

    if (active) {
      account.setActive(true);
    } else if (inactive) {
      account.setActive(false);
    }

    final String readAddKey = readSshKey(addSshKey);

    if (readAddKey != null) {
      addSshKey(readAddKey, account);
    }

    final String readDeleteKey = readSshKey(deleteSshKey);
    if (readDeleteKey != null) {
      deleteSshKeys(readDeleteKey, account);
    }

    db.accounts().update(Collections.singleton(account));
    byIdCache.evict(id);

    db.close();
  }

  private void addSshKey(final String readAddKey, final Account account)
      throws OrmException, InvalidSshKeyException {
    int seq = db.accountSshKeys().byAccount(account.getId()).toList().size();
    AccountSshKey accountKey = sshKeyCache.create(
        new AccountSshKey.Id(account.getId(), seq + 1),readAddKey);

    db.accountSshKeys().insert(Collections.singleton(accountKey));
    sshKeyCache.evict(account.getUserName());
  }

  private void deleteSshKeys(final String readDeleteKey, final Account account)
      throws OrmException {
    ResultSet<AccountSshKey> keys = db.accountSshKeys().byAccount(account.getId());
    if (readDeleteKey.equals("ALL")) {
      db.accountSshKeys().delete(keys);
    } else {
      for (AccountSshKey accountSshKey : keys) {
        if (accountSshKey.getSshPublicKey().equals(readDeleteKey)) {
          db.accountSshKeys().delete(Collections.singleton(accountSshKey));
        }
      }
    }
    sshKeyCache.evict(account.getUserName());
  }

  private void deleteMail(Account.Id id, final String mailAddress)
      throws UnloggedFailure {
    if (mailAddress.equals("ALL")) {
      ResultSet<AccountExternalId> ids;
      try {
        ids = db.accountExternalIds().byAccount(id);
      } catch (OrmException e) {
        throw die("Could not query database: " + e.getMessage());
      }
      for (AccountExternalId extId : ids) {
        unlink(id, extId.getEmailAddress());
      }
    } else {
      unlink(id, mailAddress);
    }
  }

  private void unlink(Account.Id id, final String mailAddress)
      throws UnloggedFailure {
    try {
      manager.unlink(id, AuthRequest.forEmail(mailAddress));
    } catch (AccountException ex) {
      throw die(ex.getMessage());
    }
  }

  private void link(Account.Id id, final String mailAddress)
      throws UnloggedFailure {
    try {
      manager.link(id, AuthRequest.forEmail(mailAddress));
    } catch (AccountException ex) {
      throw die(ex.getMessage());
    }
  }

  private String readSshKey(String sshKey) throws UnsupportedEncodingException,
      IOException, InvalidSshKeyException {
    if (sshKey == null) {
      return null;
    }
    if ("-".equals(sshKey)) {
      sshKey = "";
      BufferedReader br =
          new BufferedReader(new InputStreamReader(in, "UTF-8"));
      String line;
      while ((line = br.readLine()) != null) {
        sshKey += line + "\n";
      }
    }
    return sshKey.trim();
  }

}
