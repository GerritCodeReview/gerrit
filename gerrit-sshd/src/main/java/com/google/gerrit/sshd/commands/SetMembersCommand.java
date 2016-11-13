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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(
  name = "set-members",
  description = "Modify members of specific group or number of groups"
)
public class SetMembersCommand extends SshCommand {

  @Option(
    name = "--add",
    aliases = {"-a"},
    metaVar = "USER",
    usage = "users that should be added as group member"
  )
  private List<Account.Id> accountsToAdd = new ArrayList<>();

  @Option(
    name = "--remove",
    aliases = {"-r"},
    metaVar = "USER",
    usage = "users that should be removed from the group"
  )
  private List<Account.Id> accountsToRemove = new ArrayList<>();

  @Option(
    name = "--include",
    aliases = {"-i"},
    metaVar = "GROUP",
    usage = "group that should be included as group member"
  )
  private List<AccountGroup.UUID> groupsToInclude = new ArrayList<>();

  @Option(
    name = "--exclude",
    aliases = {"-e"},
    metaVar = "GROUP",
    usage = "group that should be excluded from the group"
  )
  private List<AccountGroup.UUID> groupsToRemove = new ArrayList<>();

  @Argument(
    index = 0,
    required = true,
    multiValued = true,
    metaVar = "GROUP",
    usage = "groups to modify"
  )
  private List<AccountGroup.UUID> groups = new ArrayList<>();

  @Inject private AddMembers addMembers;

  @Inject private DeleteMembers deleteMembers;

  @Inject private AddIncludedGroups addIncludedGroups;

  @Inject private DeleteIncludedGroups deleteIncludedGroups;

  @Inject private GroupsCollection groupsCollection;

  @Inject private GroupCache groupCache;

  @Inject private AccountCache accountCache;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    try {
      for (AccountGroup.UUID groupUuid : groups) {
        GroupResource resource =
            groupsCollection.parse(TopLevelResource.INSTANCE, IdString.fromUrl(groupUuid.get()));
        if (!accountsToRemove.isEmpty()) {
          deleteMembers.apply(resource, fromMembers(accountsToRemove));
          reportMembersAction("removed from", resource, accountsToRemove);
        }
        if (!groupsToRemove.isEmpty()) {
          deleteIncludedGroups.apply(resource, fromGroups(groupsToRemove));
          reportGroupsAction("excluded from", resource, groupsToRemove);
        }
        if (!accountsToAdd.isEmpty()) {
          addMembers.apply(resource, fromMembers(accountsToAdd));
          reportMembersAction("added to", resource, accountsToAdd);
        }
        if (!groupsToInclude.isEmpty()) {
          addIncludedGroups.apply(resource, fromGroups(groupsToInclude));
          reportGroupsAction("included to", resource, groupsToInclude);
        }
      }
    } catch (RestApiException e) {
      throw die(e.getMessage());
    }
  }

  private void reportMembersAction(
      String action, GroupResource group, List<Account.Id> accountIdList)
      throws UnsupportedEncodingException, IOException {
    String names =
        accountIdList
            .stream()
            .map(
                accountId ->
                    MoreObjects.firstNonNull(
                        accountCache.get(accountId).getAccount().getPreferredEmail(), "n/a"))
            .collect(joining(", "));
    out.write(
        String.format("Members %s group %s: %s\n", action, group.getName(), names).getBytes(ENC));
  }

  private void reportGroupsAction(
      String action, GroupResource group, List<AccountGroup.UUID> groupUuidList)
      throws UnsupportedEncodingException, IOException {
    String names =
        groupUuidList.stream().map(uuid -> groupCache.get(uuid).getName()).collect(joining(", "));
    out.write(
        String.format("Groups %s group %s: %s\n", action, group.getName(), names).getBytes(ENC));
  }

  private AddIncludedGroups.Input fromGroups(List<AccountGroup.UUID> accounts) {
    return AddIncludedGroups.Input.fromGroups(
        accounts.stream().map(Object::toString).collect(toList()));
  }

  private AddMembers.Input fromMembers(List<Account.Id> accounts) {
    return AddMembers.Input.fromMembers(accounts.stream().map(Object::toString).collect(toList()));
  }
}
