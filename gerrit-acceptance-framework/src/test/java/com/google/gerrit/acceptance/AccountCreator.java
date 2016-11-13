// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class AccountCreator {
  private final Map<String, TestAccount> accounts;

  private final SchemaFactory<ReviewDb> reviewDbProvider;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final GroupCache groupCache;
  private final SshKeyCache sshKeyCache;
  private final AccountCache accountCache;
  private final AccountByEmailCache byEmailCache;
  private final AccountIndexer indexer;

  @Inject
  AccountCreator(
      SchemaFactory<ReviewDb> schema,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      GroupCache groupCache,
      SshKeyCache sshKeyCache,
      AccountCache accountCache,
      AccountByEmailCache byEmailCache,
      AccountIndexer indexer) {
    accounts = new HashMap<>();
    reviewDbProvider = schema;
    this.authorizedKeys = authorizedKeys;
    this.groupCache = groupCache;
    this.sshKeyCache = sshKeyCache;
    this.accountCache = accountCache;
    this.byEmailCache = byEmailCache;
    this.indexer = indexer;
  }

  public synchronized TestAccount create(
      String username, String email, String fullName, String... groups) throws Exception {
    TestAccount account = accounts.get(username);
    if (account != null) {
      return account;
    }
    try (ReviewDb db = reviewDbProvider.open()) {
      Account.Id id = new Account.Id(db.nextAccountId());

      AccountExternalId extUser =
          new AccountExternalId(
              id, new AccountExternalId.Key(AccountExternalId.SCHEME_USERNAME, username));
      String httpPass = "http-pass";
      extUser.setPassword(httpPass);
      db.accountExternalIds().insert(Collections.singleton(extUser));

      if (email != null) {
        AccountExternalId extMailto = new AccountExternalId(id, getEmailKey(email));
        extMailto.setEmailAddress(email);
        db.accountExternalIds().insert(Collections.singleton(extMailto));
      }

      Account a = new Account(id, TimeUtil.nowTs());
      a.setFullName(fullName);
      a.setPreferredEmail(email);
      db.accounts().insert(Collections.singleton(a));

      if (groups != null) {
        for (String n : groups) {
          AccountGroup.NameKey k = new AccountGroup.NameKey(n);
          AccountGroup g = groupCache.get(k);
          checkArgument(g != null, "group not found: %s", n);
          AccountGroupMember m = new AccountGroupMember(new AccountGroupMember.Key(id, g.getId()));
          db.accountGroupMembers().insert(Collections.singleton(m));
        }
      }

      KeyPair sshKey = genSshKey();
      authorizedKeys.addKey(id, publicKey(sshKey, email));
      sshKeyCache.evict(username);

      accountCache.evictByUsername(username);
      byEmailCache.evict(email);

      indexer.index(id);

      account = new TestAccount(id, username, email, fullName, sshKey, httpPass);
      accounts.put(username, account);
      return account;
    }
  }

  public TestAccount create(String username, String group) throws Exception {
    return create(username, null, username, group);
  }

  public TestAccount create(String username) throws Exception {
    return create(username, null, username, (String[]) null);
  }

  public TestAccount admin() throws Exception {
    return create("admin", "admin@example.com", "Administrator", "Administrators");
  }

  public TestAccount admin2() throws Exception {
    return create("admin2", "admin2@example.com", "Administrator2", "Administrators");
  }

  public TestAccount user() throws Exception {
    return create("user", "user@example.com", "User");
  }

  public TestAccount user2() throws Exception {
    return create("user2", "user2@example.com", "User2");
  }

  public TestAccount get(String username) {
    return checkNotNull(accounts.get(username), "No TestAccount created for %s", username);
  }

  private AccountExternalId.Key getEmailKey(String email) {
    return new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
  }

  public static KeyPair genSshKey() throws JSchException {
    JSch jsch = new JSch();
    return KeyPair.genKeyPair(jsch, KeyPair.RSA);
  }

  public static String publicKey(KeyPair sshKey, String comment)
      throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePublicKey(out, comment);
    return out.toString(US_ASCII.name()).trim();
  }
}
