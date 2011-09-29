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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.sshd.commands.TreeFormatter.TreeNode;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

public class AccountNode implements TreeNode {

  public interface Factory {
    AccountNode create(final Account account);
  }

  private final AccountFormatter accountFormatter;
  private final Account account;

  @Inject
  public AccountNode(final AccountFormatter accountFormatter,
      @Assisted final Account account) {
    this.accountFormatter = accountFormatter;
    this.account = account;
  }

  public Account getAccount() {
    return account;
  }

  @Override
  public String getDisplayName() {
    return accountFormatter.formatWithFullnameEmailAndId(account);
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public SortedSet<TreeNode> getChildren() {
    return Collections.unmodifiableSortedSet(new TreeSet<TreeNode>());
  }
}
