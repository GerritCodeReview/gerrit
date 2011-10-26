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

import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

public class RemoveGroupInclude implements Callable<GroupMemberResult> {

  public interface Factory {
    RemoveGroupInclude create(AccountGroup.Id groupId,
        Collection<AccountGroup.Id> groupsToInclude);
  }

  private final IdentifiedUser currentUser;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;

  private final AccountGroup.Id groupId;
  private final Collection<AccountGroup.Id> groupsToRemove;

  @Inject
  RemoveGroupInclude(final IdentifiedUser currentUser,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory,
      final GroupCache groupCache, final GroupIncludeCache groupIncludeCache,
      final ReviewDb db, final @Assisted AccountGroup.Id groupId,
      final @Assisted Collection<AccountGroup.Id> groupsToInclude) {
    this.currentUser = currentUser;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.groupId = groupId;
    this.groupsToRemove = groupsToInclude;
  }

  @Override
  public GroupMemberResult call() throws Exception {
    final GroupControl control = groupControlFactory.validateFor(groupId);
    if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
      throw new NameAlreadyUsedException();
    }

    final GroupMemberResult result = new GroupMemberResult();
    final Account.Id me = currentUser.getAccountId();
    for (final AccountGroup.Id g : groupsToRemove) {
      final AccountGroup includedGroup = groupCache.get(g);
      final AccountGroupInclude.Key key =
          new AccountGroupInclude.Key(groupId, g);
      final AccountGroupInclude m = db.accountGroupIncludes().get(key);
      if (m != null) {
        if (!control.canRemoveGroup(m.getIncludeId())) {
          result.addError(new GroupMemberResult.Error(
              GroupMemberResult.Error.Type.REMOVE_NOT_PERMITTED,
              includedGroup.getName()));
          continue;
        }

        AccountGroupIncludeAudit audit = null;
        for (AccountGroupIncludeAudit a : db
            .accountGroupIncludesAudit().byGroupInclude(
                m.getGroupId(), m.getIncludeId())) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me);
          db.accountGroupIncludesAudit().update(
              Collections.singleton(audit));
        }
        db.accountGroupIncludes().delete(Collections.singleton(m));
        groupIncludeCache.evictInclude(includedGroup.getGroupUUID());
      }
    }
    result.setGroup(groupDetailFactory.create(groupId).call());
    return result;
  }
}
