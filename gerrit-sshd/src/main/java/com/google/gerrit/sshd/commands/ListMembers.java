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

import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.account.PerformGroupMembers;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ListMembers extends BaseCommand {

  private static final String NOT_VISIBLE_GROUP = "(x)";

  @Option(name = "--recursive", aliases = {"-r"}, usage = "to resolve groups recursively")
  private boolean recursive;

  @Option(name = "--project", aliases = {"-p"}, metaVar = "NAME", usage = "project to resolve 'Project Owners' group")
  private ProjectControl project;

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of group for which the members should be listed")
  private AccountGroup.UUID groupUUID;

  @Inject
  private PerformGroupMembers.Factory performGroupMembersFactory;

  @Inject
  private @AnonymousCowardName String anonymousCowardName;

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        try {
          final PerformGroupMembers performGroupMembers =
              performGroupMembersFactory.create();
          performGroupMembers.setProject(project);
          performGroupMembers.setRecursive(recursive);
          display(performGroupMembers.listMembers(groupUUID));
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

  private void display(final GroupMembers groupMembers) {
    final PrintWriter stdout = toPrintWriter(out);
    try {
      printWarningsForUnresolvedGroups(stdout, groupMembers);
      final Set<Account> accounts = groupMembers.getAllAccounts();
      final Set<GroupMembers> includedGroups =
          groupMembers.getAllIncludedGroups();
      if (!accounts.isEmpty()) {
        stdout.print("Accounts:\n");
        for (final Account account : accounts) {
          printAccount(stdout, account);
        }
        if (!includedGroups.isEmpty()) {
          stdout.print("\n");
        }
      }
      if (!includedGroups.isEmpty()) {
        stdout.print("Included Groups:\n");
        for (final GroupMembers includedGroupMembers : includedGroups) {
          printGroup(stdout, includedGroupMembers);
        }
      }
    } finally {
      stdout.flush();
    }
  }

  private void printWarningsForUnresolvedGroups(final PrintWriter stdout,
      final GroupMembers groupMembers) {
    boolean hasWarning = false;
    if (groupMembers.containsNonVisibleGroup()) {
      if (recursive) {
        stdout
            .print("Warning: Some groups are not visible to you and could not be resolved. "
                + "They are marked with '" + NOT_VISIBLE_GROUP + "'\n");
      } else {
        stdout.print("Some groups are not visible to you. "
            + "They are marked with '" + NOT_VISIBLE_GROUP + "'\n");
      }
      hasWarning = true;
    }

    final Map<AccountGroup.UUID, GroupMembers> systemGroups =
        new HashMap<AccountGroup.UUID, GroupMembers>();
    if (recursive) {
      systemGroups.putAll(groupMembers.getGroups(AccountGroup.Type.SYSTEM));
    } else {
      if (AccountGroup.Type.SYSTEM.equals(groupMembers.getGroup().getType())) {
        systemGroups.put(groupMembers.getGroup().getGroupUUID(), groupMembers);
      }
    }

    if (systemGroups.containsKey(AccountGroup.PROJECT_OWNERS)
        && project == null) {
      stdout
          .print("Warning: The group '"
              + systemGroups.get(AccountGroup.PROJECT_OWNERS).getGroup().getName()
              + "' could not be resolved. It can only be resolved for a concrete project. "
              + "Please specify a project with the '--project' option.\n");
      hasWarning = true;
    }
    systemGroups.remove(AccountGroup.PROJECT_OWNERS);

    for (final GroupMembers systemGroup : systemGroups.values()) {
      stdout.print("Warning: The group '" + systemGroup.getGroup().getName()
          + "' cannot be resolved.\n");
      hasWarning = true;
    }

    if (hasWarning) {
      stdout.print("\n");
    }
  }

  private void printAccount(final PrintWriter stdout, final Account account) {
    if (account.getFullName() != null) {
      stdout.print(account.getFullName());
    } else {
      stdout.print(anonymousCowardName);
    }
    if (account.getPreferredEmail() != null) {
      stdout.print(" <" + account.getPreferredEmail() + ">");
    }
    stdout.print(" (" + account.getId() + ")");
    stdout.print("\n");
  }

  private void printGroup(final PrintWriter stdout,
      final GroupMembers groupMembers) {
    final AccountGroup group = groupMembers.getGroup();
    stdout.print(group.getName());
    stdout.print(" (" + group.getId() + ")");
    if (!groupMembers.isGroupVisible()) {
      stdout.print(" " + NOT_VISIBLE_GROUP);
    }
    stdout.print("\n");
  }
}
