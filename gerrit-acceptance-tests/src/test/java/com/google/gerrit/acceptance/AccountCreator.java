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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collections;

import javax.inject.Inject;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

public class AccountCreator {

  private SchemaFactory<ReviewDb> reviewDbProvider;
  private GroupCache groupCache;
  private SshKeyCache sshKeyCache;
  private AccountCache accountCache;
  private AccountByEmailCache byEmailCache;

  @Inject
  AccountCreator(SchemaFactory<ReviewDb> schema, GroupCache groupCache,
      SshKeyCache sshKeyCache, AccountCache accountCache,
      AccountByEmailCache byEmailCache) {
    reviewDbProvider = schema;
    this.groupCache = groupCache;
    this.sshKeyCache = sshKeyCache;
    this.accountCache = accountCache;
    this.byEmailCache = byEmailCache;
  }

  public TestAccount create(String username, String email, String fullName,
      String... groups)
      throws OrmException, UnsupportedEncodingException, JSchException {
    ReviewDb db = reviewDbProvider.open();
    try {
      Account.Id id = new Account.Id(db.nextAccountId());
      KeyPair sshKey = genSshKey();
      AccountSshKey key =
          new AccountSshKey(new AccountSshKey.Id(id, 1), publicKey(sshKey, email));
      AccountExternalId extUser =
          new AccountExternalId(id, new AccountExternalId.Key(
              AccountExternalId.SCHEME_USERNAME, username));
      String httpPass = "http-pass";
      extUser.setPassword(httpPass);
      db.accountExternalIds().insert(Collections.singleton(extUser));

      if (email != null) {
        AccountExternalId extMailto = new AccountExternalId(id, getEmailKey(email));
        extMailto.setEmailAddress(email);
        db.accountExternalIds().insert(Collections.singleton(extMailto));
      }

      Account a = new Account(id);
      a.setFullName(fullName);
      a.setPreferredEmail(email);
      db.accounts().insert(Collections.singleton(a));

      db.accountSshKeys().insert(Collections.singleton(key));

      for (String n : groups) {
        AccountGroup.NameKey k = new AccountGroup.NameKey(n);
        AccountGroup g = groupCache.get(k);
        AccountGroupMember m =
            new AccountGroupMember(new AccountGroupMember.Key(id, g.getId()));
        db.accountGroupMembers().insert(Collections.singleton(m));
      }

      sshKeyCache.evict(username);
      accountCache.evictByUsername(username);
      byEmailCache.evict(email);

      return new TestAccount(id, username, email, fullName, sshKey, httpPass);
    } finally {
      db.close();
    }
  }

  public TestAccount create(String username, String group)
      throws OrmException, UnsupportedEncodingException, JSchException {
    return create(username, null, username, group);
  }

  private AccountExternalId.Key getEmailKey(String email) {
    return new AccountExternalId.Key(AccountExternalId.SCHEME_MAILTO, email);
  }

  private static KeyPair genSshKey() throws JSchException {
    JSch jsch = new JSch();
    return KeyPair.genKeyPair(jsch, KeyPair.RSA);
  }

  private static String publicKey(KeyPair sshKey, String comment)
      throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePublicKey(out, comment);
    return out.toString("ASCII");
  }
}
