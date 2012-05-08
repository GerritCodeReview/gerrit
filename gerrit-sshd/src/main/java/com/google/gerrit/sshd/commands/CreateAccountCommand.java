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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gerrit.sshd.RequiresCapability;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Create a new user account. **/
@RequiresCapability(GlobalCapability.CREATE_ACCOUNT)
final class CreateAccountCommand extends SshCommand {
  @Option(name = "--group", aliases = {"-g"}, metaVar = "GROUP", usage = "groups to add account to")
  private List<AccountGroup.Id> groups = new ArrayList<AccountGroup.Id>();

  @Option(name = "--full-name", metaVar = "NAME", usage = "display name of the account")
  private String fullName;

  @Option(name = "--email", metaVar = "EMAIL", usage = "email address of the account")
  private String email;

  @Option(name = "--ssh-key", metaVar = "-|KEY", usage = "public key for SSH authentication")
  private String sshKey;

  @Argument(index = 0, required = true, metaVar = "USERNAME", usage = "name of the user account")
  private String username;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

  @Inject
  private SshKeyCache sshKeyCache;

  @Inject
  private AccountCache accountCache;

  @Inject
  private AccountByEmailCache byEmailCache;

  @Override
  protected void run() throws OrmException, IOException,
      InvalidSshKeyException, UnloggedFailure {
    if (!username.matches(Account.USER_NAME_PATTERN)) {
      throw die("Username '" + username + "'"
          + " must contain only letters, numbers, _, - or .");
    }

    final Account.Id id = new Account.Id(db.nextAccountId());
    final AccountSshKey key = readSshKey(id);

    AccountExternalId extUser =
        new AccountExternalId(id, new AccountExternalId.Key(
            AccountExternalId.SCHEME_USERNAME, username));

    if (db.accountExternalIds().get(extUser.getKey()) != null) {
      throw die("username '" + username + "' already exists");
    }
    if (email != null && db.accountExternalIds().get(getEmailKey()) != null) {
      throw die("email '" + email + "' already exists");
    }

    try {
      db.accountExternalIds().insert(Collections.singleton(extUser));
    } catch (OrmDuplicateKeyException duplicateKey) {
      throw die("username '" + username + "' already exists");
    }

    if (email != null) {
      AccountExternalId extMailto = new AccountExternalId(id, getEmailKey());
      extMailto.setEmailAddress(email);
      try {
        db.accountExternalIds().insert(Collections.singleton(extMailto));
      } catch (OrmDuplicateKeyException duplicateKey) {
        try {
          db.accountExternalIds().delete(Collections.singleton(extUser));
        } catch (OrmException cleanupError) {
        }
        throw die("email '" + email + "' already exists");
      }
    }

    Account a = new Account(id);
    a.setFullName(fullName);
    a.setPreferredEmail(email);
    db.accounts().insert(Collections.singleton(a));

    if (key != null) {
      db.accountSshKeys().insert(Collections.singleton(key));
    }

    for (AccountGroup.Id groupId : new HashSet<AccountGroup.Id>(groups)) {
      AccountGroupMember m =
          new AccountGroupMember(new AccountGroupMember.Key(id, groupId));
      db.accountGroupMembersAudit().insert(Collections.singleton( //
          new AccountGroupMemberAudit(m, currentUser.getAccountId())));
      db.accountGroupMembers().insert(Collections.singleton(m));
    }

    sshKeyCache.evict(username);
    accountCache.evictByUsername(username);
    byEmailCache.evict(email);
  }

  private AccountExternalId.Key getEmailKey() {
    return new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
  }

  private AccountSshKey readSshKey(final Account.Id id)
      throws UnsupportedEncodingException, IOException, InvalidSshKeyException {
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
    return sshKeyCache.create(new AccountSshKey.Id(id, 1), sshKey.trim());
  }
}
