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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.Account;

import com.jcraft.jsch.KeyPair;

import org.eclipse.jgit.lib.PersonIdent;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;


public class TestAccount {
  public static Iterable<Account.Id> ids(Iterable<TestAccount> accounts) {
    return Iterables.transform(accounts,
        new Function<TestAccount, Account.Id>() {
          @Override
          public Account.Id apply(TestAccount in) {
            return in.id;
          }
        });
  }

  public static Iterable<Account.Id> ids(TestAccount... accounts) {
    return ids(Arrays.asList(accounts));
  }

  public final Account.Id id;
  public final String username;
  public final String email;
  public final String fullName;
  public final KeyPair sshKey;
  public final String httpPassword;

  TestAccount(Account.Id id, String username, String email, String fullName,
      KeyPair sshKey, String httpPassword) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.fullName = fullName;
    this.sshKey = sshKey;
    this.httpPassword = httpPassword;
  }

  public byte[] privateKey() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePrivateKey(out);
    return out.toByteArray();
  }

  public PersonIdent getIdent() {
    return new PersonIdent(username, email);
  }

  public String getHttpUrl(GerritServer server) {
    return String.format("http://%s:%s@%s:%d",
        username,
        httpPassword,
        server.getHttpAddress().getAddress().getHostAddress(),
        server.getHttpAddress().getPort());
  }

  public Account.Id getId() {
    return id;
  }
}
