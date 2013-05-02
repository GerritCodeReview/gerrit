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
import com.google.gerrit.sshd.CommandMetaData;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Set a user's account settings. **/
@CommandMetaData(name = "set-account", descr = "Change an account's settings")
final class SetAccountCommand extends BaseCommand {

  @Argument(index = 0, required = true, metaVar = "USER", usage = "full name, email-address, ssh username or account id")
  private Account.Id id;

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--active", usage = "set account's state to active")
  private boolean active;

  @Option(name = "--inactive", usage = "set account's state to inactive")
  private boolean inactive;

  @Option(name = "--add-email", multiValued = true, metaVar = "EMAIL", usage = "email addresses to add to the account")
  private List<String> addEmails = new ArrayList<String>();

  @Option(name = "--delete-email", multiValued = true, metaVar = "EMAIL", usage = "email addresses to delete from the account")
  private List<String> deleteEmails = new ArrayList<String>();

  @Option(name = "--add-ssh-key", multiValued = true, metaVar = "-|KEY", usage = "public keys to add to the account")
  private List<String> addSshKeys = new ArrayList<String>();

  @Option(name = "--delete-ssh-key", multiValued = true, metaVar = "-|KEY", usage = "public keys to delete from the account")
  private List<String> deleteSshKeys = new ArrayList<String>();

  @Option(name = "--http-password", metaVar = "PASSWORD", usage = "password for HTTP authentication for the account")
  private String httpPassword;

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
        validate();
        setAccount();
      }
    });
  }

  private void validate() throws UnloggedFailure {
    if (active && inactive) {
      throw new UnloggedFailure(1,
          "--active and --inactive options are mutually exclusive.");
    }
    if (addSshKeys.contains("-") && deleteSshKeys.contains("-")) {
      throw new UnloggedFailure(1, "Only one option may use the stdin");
    }
    if (deleteSshKeys.contains("ALL")) {
      deleteSshKeys = Collections.singletonList("ALL");
    }
    if (deleteEmails.contains("ALL")) {
      deleteEmails = Collections.singletonList("ALL");
    }
  }

  private void setAccount() throws OrmException, IOException, UnloggedFailure {

    final Account account = db.accounts().get(id);
    boolean accountUpdated = false;
    boolean sshKeysUpdated = false;

    for (String email : addEmails) {
      link(id, email);
    }

    for (String email : deleteEmails) {
      deleteMail(id, email);
    }

    if (fullName != null) {
      if (realm.allowsEdit(FieldName.FULL_NAME)) {
        account.setFullName(fullName);
        accountUpdated = true;
      } else {
        throw new UnloggedFailure(1, "The realm doesn't allow editing names");
      }
    }

    if (httpPassword != null) {
      setHttpPassword(id, httpPassword);
    }

    if (active) {
      accountUpdated = true;
      account.setActive(true);
    } else if (inactive) {
      accountUpdated = true;
      account.setActive(false);
    }

    addSshKeys = readSshKey(addSshKeys);
    if (!addSshKeys.isEmpty()) {
      sshKeysUpdated = true;
      addSshKeys(addSshKeys, account);
    }

    deleteSshKeys = readSshKey(deleteSshKeys);
    if (!deleteSshKeys.isEmpty()) {
      sshKeysUpdated = true;
      deleteSshKeys(deleteSshKeys, account);
    }

    if (accountUpdated) {
      db.accounts().update(Collections.singleton(account));
      byIdCache.evict(id);
    }

    if (sshKeysUpdated) {
      sshKeyCache.evict(account.getUserName());
    }
  }

  private void addSshKeys(final List<String> keys, final Account account)
      throws OrmException, UnloggedFailure {
    List<AccountSshKey> accountKeys = new ArrayList<AccountSshKey>();
    int seq = db.accountSshKeys().byAccount(account.getId()).toList().size();
    for (String key : keys) {
      try {
        seq++;
        AccountSshKey accountSshKey = sshKeyCache.create(
            new AccountSshKey.Id(account.getId(), seq), key.trim());
        accountKeys.add(accountSshKey);
      } catch (InvalidSshKeyException e) {
        throw new UnloggedFailure(1, "fatal: invalid ssh key");
      }
    }
    db.accountSshKeys().insert(accountKeys);
  }

  private void deleteSshKeys(final List<String> keys, final Account account)
      throws OrmException {
    ResultSet<AccountSshKey> allKeys = db.accountSshKeys().byAccount(account.getId());
    if (keys.contains("ALL")) {
      db.accountSshKeys().delete(allKeys);
    } else {
      List<AccountSshKey> accountKeys = new ArrayList<AccountSshKey>();
      for (String key : keys) {
        for (AccountSshKey accountSshKey : allKeys) {
          if (key.trim().equals(accountSshKey.getSshPublicKey())
              || accountSshKey.getComment().trim().equals(key)) {
            accountKeys.add(accountSshKey);
          }
        }
      }
      db.accountSshKeys().delete(accountKeys);
    }
  }

  private void deleteMail(Account.Id id, final String mailAddress)
      throws UnloggedFailure, OrmException {
    if (mailAddress.equals("ALL")) {
      ResultSet<AccountExternalId> ids = db.accountExternalIds().byAccount(id);
      for (AccountExternalId extId : ids) {
        if (extId.isScheme(AccountExternalId.SCHEME_MAILTO)) {
          unlink(id, extId.getEmailAddress());
        }
      }
    } else {
      AccountExternalId.Key key = new AccountExternalId.Key(
          AccountExternalId.SCHEME_MAILTO, mailAddress);
      AccountExternalId extId = db.accountExternalIds().get(key);
      if (extId != null) {
        unlink(id, mailAddress);
      }
    }
  }

  private void setHttpPassword(Account.Id id, final String httpPassword)
      throws UnloggedFailure, OrmException {
    ResultSet<AccountExternalId> ids = db.accountExternalIds().byAccount(id);
    for (AccountExternalId extId: ids) {
      if (extId.isScheme(AccountExternalId.SCHEME_USERNAME)) {
        extId.setPassword(httpPassword);
        db.accountExternalIds().update(Collections.singleton(extId));
        byIdCache.evict(id);
      }
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

  private List<String> readSshKey(final List<String> sshKeys)
      throws UnsupportedEncodingException, IOException {
    if (!sshKeys.isEmpty()) {
      String sshKey = "";
      int idx = sshKeys.indexOf("-");
      if (idx >= 0) {
        sshKey = "";
        BufferedReader br =
            new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
          sshKey += line + "\n";
        }
        sshKeys.set(idx, sshKey);
      }
    }
    return sshKeys;
  }
}
