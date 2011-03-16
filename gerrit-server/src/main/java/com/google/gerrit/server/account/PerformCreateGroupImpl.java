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
// limitations under the License

package com.google.gerrit.server.account;

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerformCreateGroupImpl implements PerformCreateGroup {

  public interface Factory {
    PerformCreateGroupImpl create();
  }

  private final ReviewDb db;
  private final AccountCache accountCache;
  private final IdentifiedUser currentUser;

  @Inject
  public PerformCreateGroupImpl(final ReviewDb db,
      final AccountCache accountCache, final IdentifiedUser currentUser) {
    this.db = db;
    this.accountCache = accountCache;
    this.currentUser = currentUser;
  }

  @Override
  public AccountGroup.Id createGroup(final String groupName,
      final String groupDescription, final AccountGroup.Id ownerGroupId,
      final Account.Id... initialMembers) throws OrmException,
      NameAlreadyUsedException {
    final AccountGroup.Id groupId =
        new AccountGroup.Id(db.nextAccountGroupId());
    final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    final AccountGroup group = new AccountGroup(nameKey, groupId);
    if (ownerGroupId != null) {
      group.setOwnerGroupId(ownerGroupId);
    }
    if (groupDescription != null) {
      group.setDescription(groupDescription);
    }
    final AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw new NameAlreadyUsedException();
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, initialMembers);

    return groupId;
  }

  private void addMembers(final AccountGroup.Id groupId,
      final Account.Id... member) throws OrmException {
    final List<AccountGroupMember> memberships =
        new ArrayList<AccountGroupMember>();
    final List<AccountGroupMemberAudit> membershipsAudit =
        new ArrayList<AccountGroupMemberAudit>();
    for (Account.Id accountId : member) {
      final AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);

      final AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(membership, currentUser.getAccountId());
      membershipsAudit.add(audit);

      accountCache.evict(accountId);
    }
    db.accountGroupMembers().insert(memberships);
    db.accountGroupMembersAudit().insert(membershipsAudit);
  }

}
