// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddMembers.Input;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

public class DeleteMembers implements RestModifyView<GroupResource, Input> {
  private final Provider<AccountsCollection> accounts;
  private final AccountCache accountCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteMembers(Provider<AccountsCollection> accounts,
      AccountCache accountCache, ReviewDb db, Provider<CurrentUser> self) {
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.db = db;
    this.self = self;
  }

  @Override
  public Object apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException,
      UnprocessableEntityException, OrmException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    final Map<Account.Id, AccountGroupMember> members = getMembers(internalGroup.getId());
    final List<AccountGroupMember> toRemove = Lists.newLinkedList();

    for (final String nameOrEmail : input.members) {
      Account a = accounts.get().parse(nameOrEmail).getAccount();

      if (!control.canRemoveMember(a.getId())) {
        throw new AuthException("Cannot delete member: " + a.getFullName());
      }

      final AccountGroupMember m = members.remove(a.getId());
      if (m != null) {
        toRemove.add(m);
      }
    }

    writeAudits(toRemove);
    db.accountGroupMembers().delete(toRemove);
    for (final AccountGroupMember m : toRemove) {
      accountCache.evict(m.getAccountId());
    }

    return Response.none();
  }

  private void writeAudits(final List<AccountGroupMember> toBeRemoved)
      throws OrmException {
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();
    final List<AccountGroupMemberAudit> auditUpdates = Lists.newLinkedList();
    final List<AccountGroupMemberAudit> auditInserts = Lists.newLinkedList();
    for (final AccountGroupMember m : toBeRemoved) {
      AccountGroupMemberAudit audit = null;
      for (AccountGroupMemberAudit a : db.accountGroupMembersAudit()
          .byGroupAccount(m.getAccountGroupId(), m.getAccountId())) {
        if (a.isActive()) {
          audit = a;
          break;
        }
      }

      if (audit != null) {
        audit.removed(me, TimeUtil.nowTs());
        auditUpdates.add(audit);
      } else {
        audit = new AccountGroupMemberAudit(m, me, TimeUtil.nowTs());
        audit.removedLegacy();
        auditInserts.add(audit);
      }
    }
    db.accountGroupMembersAudit().update(auditUpdates);
    db.accountGroupMembersAudit().insert(auditInserts);
  }

  private Map<Account.Id, AccountGroupMember> getMembers(
      final AccountGroup.Id groupId) throws OrmException {
    final Map<Account.Id, AccountGroupMember> members = Maps.newHashMap();
    for (final AccountGroupMember m : db.accountGroupMembers().byGroup(groupId)) {
      members.put(m.getAccountId(), m);
    }
    return members;
  }

  static class DeleteMember implements RestModifyView<MemberResource, DeleteMember.Input> {
    static class Input {
    }

    private final Provider<DeleteMembers> delete;

    @Inject
    DeleteMember(final Provider<DeleteMembers> delete) {
      this.delete = delete;
    }

    @Override
    public Object apply(MemberResource resource, Input input)
        throws AuthException, MethodNotAllowedException,
        UnprocessableEntityException, OrmException, NoSuchGroupException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = resource.getMember().getAccountId().toString();
      return delete.get().apply(resource, in);
    }
  }
}
