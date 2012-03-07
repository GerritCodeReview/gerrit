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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.HashSet;
import java.util.Set;

/**
 * Creates a new group.
 * <p>
 * Optionally, puts an initial set of user in the newly created group.
 */
final class CreateGroupCommand extends BaseCommand {
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

  private final Set<AccountGroup.Id> initialGroups = new HashSet<AccountGroup.Id>();

  @Option(name = "--group", aliases = "-g", metaVar = "GROUP", usage = "initial set of groups to be included in the group")
  void addGroup(final AccountGroup.Id id) {
    initialGroups.add(id);
  }

  @Inject
  private PerformCreateGroup.Factory performCreateGroupFactory;

  @Override
  public void start(Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        try {
          performCreateGroupFactory.create().createGroup(groupName,
              groupDescription,
              visibleToAll,
              ownerGroupId,
              initialMembers,
              initialGroups);
        } catch (PermissionDeniedException e) {
          throw die(e);

        } catch (NameAlreadyUsedException e) {
          throw die(e);
        }
      }
    });
  }
}
