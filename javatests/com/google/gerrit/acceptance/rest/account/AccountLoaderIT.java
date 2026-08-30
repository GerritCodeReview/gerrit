// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.server.account.AccountLoader;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class AccountLoaderIT extends AbstractDaemonTest {
  @Inject private AccountLoader.Factory accountLoaderFactory;

  @Test
  public void fillAccounts_detailed() throws Exception {
    TestAccount user1 = accountCreator.create("u1", "u1@example.com", "User One", "U1");
    TestAccount user2 = accountCreator.create("u2", "u2@example.com", "User Two", "U2");

    AccountLoader loader = accountLoaderFactory.create(true);
    AccountInfo info1 = loader.get(user1.id());
    AccountInfo info2 = loader.get(user2.id());

    assertThat(info1.name).isNull();
    assertThat(info2.name).isNull();

    loader.fill();

    assertThat(info1._accountId).isEqualTo(user1.id().get());
    assertThat(info1.name).isEqualTo("User One");
    assertThat(info1.email).isEqualTo("u1@example.com");
    assertThat(info1.username).isEqualTo("u1");

    assertThat(info2._accountId).isEqualTo(user2.id().get());
    assertThat(info2.name).isEqualTo("User Two");
    assertThat(info2.email).isEqualTo("u2@example.com");
    assertThat(info2.username).isEqualTo("u2");
  }

  @Test
  public void fillAccounts_idOnly() throws Exception {
    TestAccount user1 = accountCreator.create("u_id1", "uid1@example.com", "User Id Only", null);

    AccountLoader loader = accountLoaderFactory.create(false);
    AccountInfo info = loader.get(user1.id());
    loader.fill();

    assertThat(info._accountId).isEqualTo(user1.id().get());
    assertThat(info.name).isNull();
    assertThat(info.email).isNull();
  }

  @Test
  public void fillAccounts_missingAccount() throws Exception {
    Account.Id missingId = Account.id(999999);
    AccountLoader loader = accountLoaderFactory.create(true);
    AccountInfo info = loader.get(missingId);
    loader.fill();

    assertThat(info._accountId).isEqualTo(999999);
    assertThat(info.deleted).isTrue();
    assertThat(info.name).isNull();
  }

  @Test
  public void fillAccounts_duplicateInstances() throws Exception {
    TestAccount user1 = accountCreator.create("dup1", "dup1@example.com", "Dup User", null);

    AccountLoader loader = accountLoaderFactory.create(true);
    AccountInfo prime = loader.get(user1.id());
    AccountInfo dup1 = new AccountInfo(user1.id().get());
    AccountInfo dup2 = new AccountInfo(user1.id().get());
    loader.put(dup1);
    loader.put(dup2);

    loader.fill();

    assertThat(prime.name).isEqualTo("Dup User");
    assertThat(dup1.name).isEqualTo("Dup User");
    assertThat(dup1.email).isEqualTo("dup1@example.com");
    assertThat(dup2.name).isEqualTo("Dup User");
    assertThat(dup2.email).isEqualTo("dup1@example.com");
  }

  @Test
  public void fillOne() throws Exception {
    TestAccount user1 = accountCreator.create("fill_one", "fillone@example.com", "Fill One", null);

    AccountLoader loader = accountLoaderFactory.create(true);
    AccountInfo info = loader.fillOne(user1.id());

    assertThat(info).isNotNull();
    assertThat(info._accountId).isEqualTo(user1.id().get());
    assertThat(info.name).isEqualTo("Fill One");
    assertThat(info.email).isEqualTo("fillone@example.com");
  }

  @Test
  public void fillAccounts_empty() throws Exception {
    AccountLoader loader = accountLoaderFactory.create(true);
    loader.fill();
    assertThat(loader.get(null)).isNull();
  }
}
