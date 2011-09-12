// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.AccountFormatter;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.account.GroupMembersList;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ListMembersCommand extends BaseCommand {

  @Option(name = "--recursive", aliases = {"-r"},
      usage = "to resolve groups recursively")
  private boolean recursive;

  @Option(name = "--project", aliases = {"-p"}, metaVar = "NAME",
      usage = "project to resolve 'Project Owners' group")
  private ProjectControl project;

  @Argument(index = 0, required = true, metaVar = "GROUP",
      usage = "name of group for which the members should be listed")
  private AccountGroup.UUID groupUUID;

  @Inject
  private GroupMembers.Factory groupMembersFactory;

  @Inject
  private AccountFormatter accountFormatter;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        try {
          display(groupMembersFactory.create().listMembers(groupUUID, project,
              recursive));
        } catch (NoSuchGroupException e) {
          throw die(e);
        } catch (NoSuchProjectException e) {
          throw die(e);
        } catch (OrmException e) {
          throw die(e);
        }
      }
    });
  }

  private void display(final GroupMembersList groupMembers) {
    final PrintWriter stdout = toPrintWriter(out);
    try {
      printWarnings(stdout, groupMembers);
      final Set<Account> accounts = groupMembers.getAllAccounts();
      final Set<GroupMembersList> includedGroups =
          groupMembers.getAllIncludedGroups();
      if (!accounts.isEmpty()) {
        stdout.print("Accounts:\n");
        for (final Account account : accounts) {
          stdout.print(accountFormatter.formatWithFullnameEmailAndId(account) + "\n");
        }
        if (!includedGroups.isEmpty()) {
          stdout.print("\n");
        }
      }
      if (!includedGroups.isEmpty()) {
        stdout.print("Included Groups:\n");
        for (final GroupMembersList includedGroupMembers : includedGroups) {
          if (includedGroupMembers.isGroupVisible()) {
            final AccountGroup group = includedGroupMembers.getGroup();
            stdout.print(group.getName() + " (" + group.getId() + ")\n");
          }
        }
      }
    } finally {
      stdout.flush();
    }
  }

  private void printWarnings(final PrintWriter stdout,
      final GroupMembersList groupMembers) {
    boolean hasWarning = false;

    if (groupMembers.containsNonVisibleGroup()) {
      stdout.print("Warning: Some included groups are not visible to you "
          + "and are not shown in the result.\n");
      hasWarning = true;
    }

    final Map<AccountGroup.UUID, GroupMembersList> unresolvedGroups =
        new HashMap<AccountGroup.UUID, GroupMembersList>();
    unresolvedGroups.putAll(groupMembers.getUnresolvedGroups(recursive));

    if (unresolvedGroups.containsKey(AccountGroup.PROJECT_OWNERS)) {
      stdout.print("Warning: The group '"
              + unresolvedGroups.get(AccountGroup.PROJECT_OWNERS).getGroup().getName()
              + "' could not be resolved. It can only be resolved for a concrete project. "
              + "Please specify a project with the '--project' option.\n");
      hasWarning = true;
    }
    unresolvedGroups.remove(AccountGroup.PROJECT_OWNERS);

    for (final GroupMembersList unresolvedGroup : unresolvedGroups.values()) {
      stdout.print("Warning: The group '" + unresolvedGroup.getGroup().getName()
          + "' cannot be resolved.\n");
      hasWarning = true;
    }

    if (hasWarning) {
      stdout.print("\n");
    }
  }
}
