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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AccountCreator {
  private final Map<String, TestAccount> accounts;

  private final Sequences sequences;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final GroupCache groupCache;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  AccountCreator(
      Sequences sequences,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      GroupCache groupCache,
      @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    accounts = new HashMap<>();
    this.sequences = sequences;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.groupCache = groupCache;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  public synchronized TestAccount create(
      @Nullable String username,
      @Nullable String email,
      @Nullable String fullName,
      String... groupNames)
      throws Exception {

    TestAccount account = accounts.get(username);
    if (account != null) {
      return account;
    }
    Account.Id id = new Account.Id(sequences.nextAccountId());

    List<ExternalId> extIds = new ArrayList<>(2);
    String httpPass = null;
    if (username != null) {
      httpPass = "http-pass";
      extIds.add(ExternalId.createUsername(username, id, httpPass));
    }

    if (email != null) {
      extIds.add(ExternalId.createEmail(id, email));
    }

    accountsUpdateProvider
        .get()
        .insert(
            "Create Test Account",
            id,
            u -> u.setFullName(fullName).setPreferredEmail(email).addExternalIds(extIds));

    if (groupNames != null) {
      for (String n : groupNames) {
        AccountGroup.NameKey k = new AccountGroup.NameKey(n);
        Optional<InternalGroup> group = groupCache.get(k);
        if (!group.isPresent()) {
          throw new NoSuchGroupException(n);
        }
        addGroupMember(group.get().getGroupUUID(), id);
      }
    }

    account = new TestAccount(id, username, email, fullName, httpPass);
    if (username != null) {
      accounts.put(username, account);
    }
    return account;
  }

  public TestAccount create(@Nullable String username, String group) throws Exception {
    return create(username, null, username, group);
  }

  public TestAccount create() throws Exception {
    return create(null);
  }

  public TestAccount create(@Nullable String username) throws Exception {
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

  public void evict(Collection<Account.Id> ids) {
    accounts.values().removeIf(a -> ids.contains(a.id));
  }

  private void addGroupMember(AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ImmutableSet.of(accountId)))
            .build();
    groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
  }
}
