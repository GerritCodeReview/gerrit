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

import static java.util.stream.Collectors.toList;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.AddIncludedGroups;
import com.google.gerrit.server.group.AddMembers;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Creates a new group.
 *
 * <p>Optionally, puts an initial set of user in the newly created group.
 */
@RequiresCapability(GlobalCapability.CREATE_GROUP)
@CommandMetaData(name = "create-group", description = "Create a new account group")
final class CreateGroupCommand extends SshCommand {
  @Option(
    name = "--owner",
    aliases = {"-o"},
    metaVar = "GROUP",
    usage = "owning group, if not specified the group will be self-owning"
  )
  private AccountGroup.Id ownerGroupId;

  @Option(
    name = "--description",
    aliases = {"-d"},
    metaVar = "DESC",
    usage = "description of group"
  )
  private String groupDescription = "";

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of group to be created")
  private String groupName;

  private final Set<Account.Id> initialMembers = new HashSet<>();

  @Option(
    name = "--member",
    aliases = {"-m"},
    metaVar = "USERNAME",
    usage = "initial set of users to become members of the group"
  )
  void addMember(Account.Id id) {
    initialMembers.add(id);
  }

  @Option(name = "--visible-to-all", usage = "to make the group visible to all registered users")
  private boolean visibleToAll;

  private final Set<AccountGroup.UUID> initialGroups = new HashSet<>();

  @Option(
    name = "--group",
    aliases = "-g",
    metaVar = "GROUP",
    usage = "initial set of groups to be included in the group"
  )
  void addGroup(AccountGroup.UUID id) {
    initialGroups.add(id);
  }

  @Inject private CreateGroup.Factory createGroupFactory;

  @Inject private GroupsCollection groups;

  @Inject private AddMembers addMembers;

  @Inject private AddIncludedGroups addIncludedGroups;

  @Override
  protected void run() throws Failure, OrmException, IOException {
    try {
      GroupResource rsrc = createGroup();

      if (!initialMembers.isEmpty()) {
        addMembers(rsrc);
      }

      if (!initialGroups.isEmpty()) {
        addIncludedGroups(rsrc);
      }
    } catch (RestApiException e) {
      throw die(e);
    }
  }

  private GroupResource createGroup() throws RestApiException, OrmException, IOException {
    GroupInput input = new GroupInput();
    input.description = groupDescription;
    input.visibleToAll = visibleToAll;

    if (ownerGroupId != null) {
      input.ownerId = String.valueOf(ownerGroupId.get());
    }

    GroupInfo group = createGroupFactory.create(groupName).apply(TopLevelResource.INSTANCE, input);
    return groups.parse(TopLevelResource.INSTANCE, IdString.fromUrl(group.id));
  }

  private void addMembers(GroupResource rsrc) throws RestApiException, OrmException, IOException {
    AddMembers.Input input =
        AddMembers.Input.fromMembers(
            initialMembers.stream().map(Object::toString).collect(toList()));
    addMembers.apply(rsrc, input);
  }

  private void addIncludedGroups(GroupResource rsrc) throws RestApiException, OrmException {
    AddIncludedGroups.Input input =
        AddIncludedGroups.Input.fromGroups(
            initialGroups.stream().map(AccountGroup.UUID::get).collect(toList()));
    addIncludedGroups.apply(rsrc, input);
  }
}
