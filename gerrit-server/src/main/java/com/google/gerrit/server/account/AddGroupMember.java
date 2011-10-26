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

package com.google.gerrit.server.account;

import com.google.gerrit.common.AccountFormatter;
import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

public class AddGroupMember implements Callable<GroupMemberResult> {

  public interface Factory {
    AddGroupMember create(AccountGroup.Id groupId,
        Collection<Account.Id> accountIds);
  }

  private final IdentifiedUser currentUser;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;
  private final AccountFormatter accountFormatter;
  private final ReviewDb db;

  private final AccountGroup.Id groupId;
  private final Collection<Account.Id> accountIds;

  @Inject
  AddGroupMember(final IdentifiedUser currentUser,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache, final AccountFormatter accountFormatter,
      final ReviewDb db, final @Assisted AccountGroup.Id groupId,
      final @Assisted Collection<Account.Id> accountIds) {
    this.currentUser = currentUser;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.accountCache = accountCache;
    this.accountFormatter = accountFormatter;
    this.db = db;
    this.groupId = groupId;
    this.accountIds = accountIds;
  }

  @Override
  public GroupMemberResult call() throws Exception {
    final GroupControl control = groupControlFactory.validateFor(groupId);
    if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
      throw new NameAlreadyUsedException();
    }

    final GroupMemberResult result = new GroupMemberResult();
    for (final Account.Id accountId : accountIds) {
      final Account a = accountCache.get(accountId).getAccount();
      if (!a.isActive()) {
        result.addError(new GroupMemberResult.Error(
            GroupMemberResult.Error.Type.ACCOUNT_INACTIVE,
            accountFormatter.format(a)));
        continue;
      }
      if (!control.canAddMember(a.getId())) {
        result.addError(new GroupMemberResult.Error(
            GroupMemberResult.Error.Type.ADD_NOT_PERMITTED,
            accountFormatter.format(a)));
        continue;
      }

      final AccountGroupMember.Key key =
          new AccountGroupMember.Key(a.getId(), groupId);
      AccountGroupMember m = db.accountGroupMembers().get(key);
      if (m == null) {
        m = new AccountGroupMember(key);
        db.accountGroupMembersAudit().insert(
            Collections.singleton(new AccountGroupMemberAudit(m, currentUser
                .getAccountId())));
        db.accountGroupMembers().insert(Collections.singleton(m));
        accountCache.evict(m.getAccountId());
      }
    }

    result.setGroup(groupDetailFactory.create(groupId).call());
    return result;
  }
}
