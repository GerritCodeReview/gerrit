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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AccountComparator;
import com.google.gerrit.server.account.GroupMembersList;
import com.google.gerrit.sshd.commands.TreeFormatter.TreeNode;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class AccountGroupNode implements TreeNode {

  public interface Factory {
    AccountGroupNode create(final GroupMembersList groupMembersList);
  }

  private static final String NO_MEMBERS = "<no-group-members/>";
  private static final String UNRESOLVED_GROUP = "<unresolved-group/>";

  private final AccountNode.Factory accountNodeFactory;
  private final GroupMembersList groupMembersList;

  @Inject
  public AccountGroupNode(final AccountNode.Factory accountNodeFactory,
      @Assisted final GroupMembersList groupMembersList) {
    this.accountNodeFactory = accountNodeFactory;
    this.groupMembersList = groupMembersList;
  }

  @Override
  public String getDisplayName() {
    final AccountGroup group = groupMembersList.getGroup();
    return group.getName() + " (" + group.getId() + ")";
  }

  @Override
  public boolean isVisible() {
    return groupMembersList.isGroupVisible();
  }

  @Override
  public String getDescription() {
    if (!groupMembersList.isResolved()) {
      return UNRESOLVED_GROUP;
    }

    if (groupMembersList.isEmpty() && groupMembersList.isGroupVisible()) {
      return NO_MEMBERS;
    }
    return null;
  }

  @Override
  public SortedSet<TreeNode> getChildren() {
    final SortedSet<TreeNode> children = new TreeSet<TreeNode>(new Comparator<TreeNode>() {
      private final AccountComparator accountComparator = new AccountComparator();

      @Override
      public int compare(final TreeNode node1, final TreeNode node2) {
        if (node1 instanceof AccountNode) {
          if (node2 instanceof AccountNode) {
            final Account account1 = ((AccountNode)node1).getAccount();
            final Account account2 = ((AccountNode)node2).getAccount();
            return accountComparator.compare(account1, account2);
          } else {
            return -1;
          }
        } else {
          if (node2 instanceof AccountNode) {
            return 1;
          } else {
            final AccountGroup group1 = ((AccountGroupNode)node1).groupMembersList.getGroup();
            final AccountGroup group2 = ((AccountGroupNode)node2).groupMembersList.getGroup();
            return group1.getName().compareTo(group2.getName());
          }
        }
      }
    });
    for (final Account account : groupMembersList.getAccounts()) {
      children.add(accountNodeFactory.create(account));
    }
    for (final GroupMembersList group : groupMembersList.getIncludedGroups()) {
      children.add(new AccountGroupNode(accountNodeFactory, group));
    }
    return children;
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof AccountGroupNode) {
      final AccountGroup.UUID groupUUID1 =
          groupMembersList.getGroup().getGroupUUID();
      final AccountGroup.UUID groupUUID2 =
          ((AccountGroupNode) obj).groupMembersList.getGroup().getGroupUUID();
      return groupUUID1.equals(groupUUID2);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return groupMembersList.getGroup().getGroupUUID().hashCode();
  }
}
