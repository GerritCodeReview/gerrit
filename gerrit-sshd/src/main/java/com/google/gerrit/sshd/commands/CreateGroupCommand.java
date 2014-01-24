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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.validators.GroupCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates a new group.
 * <p>
 * Optionally, puts an initial set of user in the newly created group.
 */
@RequiresCapability(GlobalCapability.CREATE_GROUP)
@CommandMetaData(name = "create-group", description = "Create a new account group")
final class CreateGroupCommand extends SshCommand {
  @Option(name = "--owner", aliases = {"-o"}, metaVar = "GROUP", usage = "owning group, if not specified the group will be self-owning")
  private AccountGroup.Id ownerGroupId;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESC", usage = "description of group")
  private String groupDescription = "";

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of group to be created")
  private String groupName;

  private final Set<Account.Id> initialMembers = new HashSet<Account.Id>();

  @Option(name = "--member", aliases = {"-m"}, metaVar = "USERNAME", usage = "initial set of users to become members of the group")
  void addMember(final Account.Id id) {
    initialMembers.add(id);
  }

  @Option(name = "--visible-to-all", usage = "to make the group visible to all registered users")
  private boolean visibleToAll;

  private final Set<AccountGroup.UUID> initialGroups = new HashSet<AccountGroup.UUID>();

  @Option(name = "--group", aliases = "-g", metaVar = "GROUP", usage = "initial set of groups to be included in the group")
  void addGroup(final AccountGroup.UUID id) {
    initialGroups.add(id);
  }

  @Inject
  private PerformCreateGroup.Factory performCreateGroupFactory;

  @Inject
  private DynamicSet<GroupCreationValidationListener> groupCreationValidationListeners;

  @Override
  protected void run() throws Failure, OrmException {
    try {
      CreateGroupArgs args = new CreateGroupArgs();
      args.setGroupName(groupName);
      args.groupDescription = groupDescription;
      args.visibleToAll = visibleToAll;
      args.ownerGroupId = ownerGroupId;
      args.initialMembers = initialMembers;
      args.initialGroups = initialGroups;

      for (GroupCreationValidationListener l : groupCreationValidationListeners) {
        try {
          l.validateNewGroup(args);
        } catch (ValidationException e) {
          die(e);
        }
      }

      performCreateGroupFactory.create(args).createGroup();
    } catch (PermissionDeniedException e) {
      throw die(e);

    } catch (NameAlreadyUsedException e) {
      throw die(e);
    }
  }
}
