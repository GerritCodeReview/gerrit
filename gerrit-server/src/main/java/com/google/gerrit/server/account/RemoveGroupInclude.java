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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class RemoveGroupInclude implements Callable<VoidResult> {

  public interface Factory {
    RemoveGroupInclude create(AccountGroup.Id groupId,
        Set<AccountGroupInclude.Key> keys);
  }

  private final IdentifiedUser currentUser;
  private final GroupControl.Factory groupControlFactory;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;

  private final AccountGroup.Id groupId;
  private final Set<AccountGroupInclude.Key> keys;

  @Inject
  RemoveGroupInclude(final IdentifiedUser currentUser,
      final GroupControl.Factory groupControlFactory,
      final GroupIncludeCache groupIncludeCache,
      final ReviewDb db, final @Assisted AccountGroup.Id groupId,
      final @Assisted Set<AccountGroupInclude.Key> keys) {
    this.currentUser = currentUser;
    this.groupControlFactory = groupControlFactory;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.groupId = groupId;
    this.keys = keys;
  }

  @Override
  public VoidResult call() throws Exception {
    final GroupControl control = groupControlFactory.validateFor(groupId);
    if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
      throw new NameAlreadyUsedException();
    }

    for (final AccountGroupInclude.Key k : keys) {
      if (!groupId.equals(k.getGroupId())) {
        throw new NoSuchEntityException();
      }
    }

    final Account.Id me = currentUser.getAccountId();
    final Set<AccountGroup.Id> groupsToEvict = new HashSet<AccountGroup.Id>();
    for (final AccountGroupInclude.Key k : keys) {
      final AccountGroupInclude m =
          db.accountGroupIncludes().get(k);
      if (m != null) {
        if (!control.canRemoveGroup(m.getIncludeId())) {
          throw new NoSuchEntityException();
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
        groupsToEvict.add(k.getIncludeId());
      }
    }
    for (AccountGroup group : db.accountGroups().get(groupsToEvict)) {
      groupIncludeCache.evictInclude(group.getGroupUUID());
    }
    return VoidResult.INSTANCE;
  }
}
