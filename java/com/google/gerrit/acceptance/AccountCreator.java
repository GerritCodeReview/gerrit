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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.notedb.Sequences;
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
  private final ExternalIdFactory externalIdFactory;

  @Inject
  AccountCreator(
      Sequences sequences,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      GroupCache groupCache,
      @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      ExternalIdFactory externalIdFactory) {
    accounts = new HashMap<>();
    this.sequences = sequences;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.groupCache = groupCache;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.externalIdFactory = externalIdFactory;
  }

  public synchronized TestAccount create(
      @Nullable String username,
      @Nullable String email,
      @Nullable String fullName,
      @Nullable String displayName,
      String... groupNames)
      throws Exception {

    TestAccount account = accounts.get(username);
    if (account != null) {
      return account;
    }
    Account.Id id = Account.id(sequences.nextAccountId());

    List<ExternalId> extIds = new ArrayList<>(2);
    String httpPass = null;
    if (username != null) {
      httpPass = "http-pass";
      extIds.add(externalIdFactory.createUsername(username, id, httpPass));
    }

    if (email != null) {
      extIds.add(externalIdFactory.createEmail(id, email));
    }

    accountsUpdateProvider
        .get()
        .insert(
            "Create Test Account",
            id,
            u ->
                u.setFullName(fullName)
                    .setDisplayName(displayName)
                    .setPreferredEmail(email)
                    .addExternalIds(extIds));

    ImmutableList.Builder<String> tags = ImmutableList.builder();
    if (groupNames != null) {
      for (String n : groupNames) {
        AccountGroup.NameKey k = AccountGroup.nameKey(n);
        Optional<InternalGroup> group = groupCache.get(k);
        if (!group.isPresent()) {
          throw new NoSuchGroupException(n);
        }
        addGroupMember(group.get().getGroupUUID(), id);
        if (ServiceUserClassifier.SERVICE_USERS.equals(n)) {
          tags.add("SERVICE_USER");
        }
      }
    }

    account =
        TestAccount.create(id, username, email, fullName, displayName, httpPass, tags.build());
    if (username != null) {
      accounts.put(username, account);
    }
    return account;
  }

  public TestAccount create(@Nullable String username, String group) throws Exception {
    return create(username, null, username, null, group);
  }

  public TestAccount create() throws Exception {
    return create(null);
  }

  public TestAccount create(@Nullable String username) throws Exception {
    return create(username, null, username, null, (String[]) null);
  }

  public TestAccount createValid(String username) throws Exception {
    return create(username, username + "@example.com", username, username);
  }

  public TestAccount admin() throws Exception {
    return create("admin", "admin@example.com", "Administrator", "Adminny", "Administrators");
  }

  public TestAccount admin2() throws Exception {
    return create("admin2", "admin2@example.com", "Administrator2", null, "Administrators");
  }

  public TestAccount user1() throws Exception {
    return create("user1", "user1@example.com", "User1", null);
  }

  public TestAccount user2() throws Exception {
    return create("user2", "user2@example.com", "User2", null);
  }

  public TestAccount get(String username) {
    return requireNonNull(
        accounts.get(username), () -> String.format("No TestAccount created for %s ", username));
  }

  public void evict(Collection<Account.Id> ids) {
    accounts.values().removeIf(a -> ids.contains(a.id()));
  }

  public ImmutableList<TestAccount> getAll() {
    return ImmutableList.copyOf(accounts.values());
  }

  private void addGroupMember(AccountGroup.UUID groupUuid, Account.Id accountId)
      throws IOException, NoSuchGroupException, ConfigInvalidException {
    GroupDelta groupDelta =
        GroupDelta.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, ImmutableSet.of(accountId)))
            .build();
    groupsUpdateProvider.get().updateGroup(groupUuid, groupDelta);
  }
}
