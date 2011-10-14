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
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;

public class RemoveGroupMember implements Callable<VoidResult> {

  public interface Factory {
    RemoveGroupMember create(AccountGroup.Id groupId,
        Set<AccountGroupMember.Key> keys);
  }

  private final IdentifiedUser currentUser;
  private final GroupControl.Factory groupControlFactory;
  private final AccountCache accountCache;
  private final ReviewDb db;

  private final AccountGroup.Id groupId;
  private final Set<AccountGroupMember.Key> keys;

  @Inject
  RemoveGroupMember(final IdentifiedUser currentUser,
      final GroupControl.Factory groupControlFactory,
      final AccountCache accountCache, final ReviewDb db,
      final @Assisted AccountGroup.Id groupId,
      final @Assisted Set<AccountGroupMember.Key> keys) {
    this.currentUser = currentUser;
    this.groupControlFactory = groupControlFactory;
    this.accountCache = accountCache;
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

    for (final AccountGroupMember.Key k : keys) {
      if (!groupId.equals(k.getAccountGroupId())) {
        throw new NoSuchEntityException();
      }
    }

    final Account.Id me = currentUser.getAccountId();
    for (final AccountGroupMember.Key k : keys) {
      final AccountGroupMember m = db.accountGroupMembers().get(k);
      if (m != null) {
        if (!control.canRemoveMember(m.getAccountId())) {
          throw new NoSuchEntityException();
        }

        AccountGroupMemberAudit audit = null;
        for (AccountGroupMemberAudit a : db.accountGroupMembersAudit()
            .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
          if (a.isActive()) {
            audit = a;
            break;
          }
        }

        if (audit != null) {
          audit.removed(me);
          db.accountGroupMembersAudit().update(Collections.singleton(audit));
        } else {
          audit = new AccountGroupMemberAudit(m, me);
          audit.removedLegacy();
          db.accountGroupMembersAudit().insert(Collections.singleton(audit));
        }

        db.accountGroupMembers().delete(Collections.singleton(m));
        accountCache.evict(m.getAccountId());
      }
    }
    return VoidResult.INSTANCE;
  }
}
