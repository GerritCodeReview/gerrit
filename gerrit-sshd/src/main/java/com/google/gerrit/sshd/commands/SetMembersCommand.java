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

package com.google.gerrit.sshd.commands;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.AddIncludedGroups;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.group.DeleteIncludedGroups;
import com.google.gerrit.server.group.DeleteMembers;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@CommandMetaData(name = "set-members", description = "Modifies members of specific group or number of groups")
public class SetMembersCommand extends SshCommand {

  @Option(name = "--add", aliases = {"-a"}, metaVar = "USER", usage = "users that should be added as group member")
  private List<Account.Id> accountsToAdd = Lists.newArrayList();

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "USER", usage = "users that should be removed from the group")
  private List<Account.Id> accountsToRemove = Lists.newArrayList();

  @Option(name = "--include", aliases = {"-i"}, metaVar = "GROUP", usage = "group that should be included as group member")
  private List<AccountGroup.UUID> groupsToInclude = Lists.newArrayList();

  @Option(name = "--exclude", aliases = {"-e"}, metaVar = "GROUP", usage = "group that should be excluded from the group")
  private List<AccountGroup.UUID> groupsToRemove = Lists.newArrayList();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "GROUP", usage = "groups to modify")
  private List<AccountGroup.UUID> groups = Lists.newArrayList();

  @Inject
  private Provider<AddMembers> addMembers;

  @Inject
  private Provider<DeleteMembers> deleteMembers;

  @Inject
  private Provider<AddIncludedGroups> addIncludedGroups;

  @Inject
  private Provider<DeleteIncludedGroups> deleteIncludedGroups;

  @Inject
  private GroupsCollection groupsCollection;

  @Inject
  private GroupCache groupCache;

  @Inject
  private AccountCache accountCache;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    for (AccountGroup.UUID groupUuid : groups) {
      GroupResource resource =
          groupsCollection.parse(TopLevelResource.INSTANCE,
              IdString.fromUrl(groupUuid.get()));
      if (!accountsToRemove.isEmpty()) {
        deleteMembers.get().apply(resource, fromMembers(accountsToRemove));
        reportMembersAction("removed from", resource, accountsToRemove);
      }
      if (!groupsToRemove.isEmpty()) {
        deleteIncludedGroups.get().apply(resource, fromGroups(groupsToRemove));
        reportGroupsAction("excluded from", resource, groupsToRemove);
      }
      if (!accountsToAdd.isEmpty()) {
        addMembers.get().apply(resource, fromMembers(accountsToAdd));
        reportMembersAction("added to", resource, accountsToAdd);
      }
      if (!groupsToInclude.isEmpty()) {
        addIncludedGroups.get().apply(resource, fromGroups(groupsToInclude));
        reportGroupsAction("included to", resource, groupsToInclude);
      }
    }
  }

  private void reportMembersAction(String action, GroupResource group,
      List<Account.Id> accountIdList) throws UnsupportedEncodingException,
      IOException {
    out.write(String.format(
        "Members %s group %s: %s\n",
        action,
        group.getName(),
        Joiner.on(", ").join(
            Iterables.transform(accountIdList,
                new Function<Account.Id, String>() {
                  @Override
                  public String apply(Account.Id accountId) {
                    return Objects.firstNonNull(accountCache.get(accountId)
                        .getAccount().getPreferredEmail(), "n/a");
                  }
                }))).getBytes(ENC));
  }

  private void reportGroupsAction(String action, GroupResource group,
      List<AccountGroup.UUID> groupUuidList)
      throws UnsupportedEncodingException, IOException {
    out.write(String.format(
        "Groups %s group %s: %s\n",
        action,
        group.getName(),
        Joiner.on(", ").join(
            Iterables.transform(groupUuidList,
                new Function<AccountGroup.UUID, String>() {
                  @Override
                  public String apply(AccountGroup.UUID uuid) {
                    return groupCache.get(uuid).getName();
                  }
                }))).getBytes(ENC));
  }

  private AddIncludedGroups.Input fromGroups(List<AccountGroup.UUID> accounts) {
    return AddIncludedGroups.Input.fromGroups(Lists.newArrayList(Iterables
        .transform(accounts, new Function<AccountGroup.UUID, String>() {
          @Override
          public String apply(AccountGroup.UUID uuid) {
            return uuid.toString();
          }
        })));
  }

  private AddMembers.Input fromMembers(List<Account.Id> accounts) {
    return AddMembers.Input.fromMembers(Lists.newArrayList(Iterables.transform(
        accounts, new Function<Account.Id, String>() {
          @Override
          public String apply(Account.Id id) {
            return id.toString();
          }
        })));
  }
}
