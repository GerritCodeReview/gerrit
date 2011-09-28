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
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

public class ListMembers extends BaseCommand {

  private static final String NODE_PREFIX = "|-- ";
  private static final String LAST_NODE_PREFIX = "`-- ";
  private static final String NO_MEMBERS = "<no-members>";
  private static final String RECURSIVE_GROUP = "...";
  private static final String UNRESOLVED_GROUP = "<unresolved>";
  private static final String NOT_VISIBLE_GROUP = "(x)";

  @Option(name = "--recursive", aliases = {"-r"}, usage = "to resolve groups recursively")
  private boolean recursive;

  @Option(name = "--project", aliases = {"-p"}, metaVar = "NAME", usage = "project to resolve 'Project Owners' group")
  private ProjectControl project;

  @Option(name = "--tree", aliases = {"-t"}, usage = "display the group hierarchy in a tree-like format")
  private boolean showTree;

  @Argument(index = 0, required = true, metaVar = "GROUP", usage = "name of group for which the members should be listed")
  private AccountGroup.UUID groupUUID;

  @Inject
  private PerformGroupMembers.Factory performGroupMembersFactory;

  @Inject
  private @AnonymousCowardName String anonymousCowardName;

  private Set<AccountGroup.UUID> unresolvedGroups = new HashSet<AccountGroup.UUID>();

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        if (showTree) {
          recursive = true;
        }
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
      if (!showTree) {
        printMemberList(stdout, groupMembers);
      } else {
        printMemberTree(stdout, groupMembers);
      }
    } finally {
      stdout.flush();
    }
  }

  private void printMemberList(final PrintWriter stdout,
      final GroupMembers groupMembers) {
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
  }

  private void printMemberTree(final PrintWriter stdout,
      final GroupMembers groupMembers) {
    printMember(stdout, groupMembers, 0, true, new HashSet<AccountGroup.Id>());
  }

  private void printMember(final PrintWriter stdout,
      final GroupMembers groupMembers, int level, final boolean isLast,
      final Set<AccountGroup.Id> seen) {
    seen.add(groupMembers.getGroup().getId());
    printIndention(stdout, level);
    stdout.print(isLast ? LAST_NODE_PREFIX : NODE_PREFIX);
    printGroup(stdout, groupMembers);

    if (groupMembers.isEmpty() && groupMembers.isGroupVisible()) {
      printIndention(stdout, level + 1);
      if (unresolvedGroups.contains(groupMembers.getGroup().getGroupUUID())) {
        stdout.print(UNRESOLVED_GROUP);
      } else {
        stdout.print(NO_MEMBERS);
      }
      stdout.print("\n");
      return;
    } else {
      ++level;
      final SortedSet<Account> accounts = groupMembers.getAccounts();
      final SortedSet<GroupMembers> groups = groupMembers.getIncludedGroups();
      for (final Account account : accounts) {
        final boolean isLastMember =
            groups.isEmpty() && accounts.last().equals(account);
        printMember(stdout, account, level, isLastMember);
      }
      for (final GroupMembers group : groups) {
        final boolean isLastMember = groups.last().equals(group);
        if (!seen.contains(group.getGroup().getId())) {
          printMember(stdout, group, level, isLastMember, seen);
        } else {
          printIndention(stdout, level);
          stdout.print(isLastMember ? LAST_NODE_PREFIX : NODE_PREFIX);
          printGroup(stdout, group);
          printIndention(stdout, level + 1);
          stdout.print(RECURSIVE_GROUP);
          stdout.print("\n");
        }
      }
    }
  }

  private void printMember(final PrintWriter stdout, final Account account,
      final int level, final boolean isLast) {
    printIndention(stdout, level);
    stdout.print(isLast ? LAST_NODE_PREFIX : NODE_PREFIX);
    printAccount(stdout, account);
  }

  private void printIndention(final PrintWriter stdout, final int level) {
    for (int i = 0; i < level; i++) {
      stdout.print("    ");
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
      unresolvedGroups.add(AccountGroup.PROJECT_OWNERS);
      hasWarning = true;
    }
    systemGroups.remove(AccountGroup.PROJECT_OWNERS);

    for (final GroupMembers systemGroup : systemGroups.values()) {
      stdout.print("Warning: The group '" + systemGroup.getGroup().getName()
          + "' cannot be resolved.\n");
      unresolvedGroups.add(systemGroup.getGroup().getGroupUUID());
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
