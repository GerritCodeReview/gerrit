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

import com.google.gerrit.audit.AuditService;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddMembers.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class DeleteMembers implements RestModifyView<GroupResource, Input> {
  private final AccountsCollection accounts;
  private final AccountCache accountCache;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> self;
  private final AuditService auditService;

  @Inject
  DeleteMembers(
      AccountsCollection accounts,
      AccountCache accountCache,
      Provider<ReviewDb> db,
      Provider<CurrentUser> self,
      AuditService auditService) {
    this.accounts = accounts;
    this.accountCache = accountCache;
    this.db = db;
    this.self = self;
    this.auditService = auditService;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
          IOException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    final Map<Account.Id, AccountGroupMember> members = getMembers(internalGroup.getId());
    final List<AccountGroupMember> toRemove = new ArrayList<>();

    for (String nameOrEmail : input.members) {
      Account a = accounts.parse(nameOrEmail).getAccount();

      if (!control.canRemoveMember()) {
        throw new AuthException("Cannot delete member: " + a.getFullName());
      }

      final AccountGroupMember m = members.remove(a.getId());
      if (m != null) {
        toRemove.add(m);
      }
    }

    writeAudits(toRemove);
    db.get().accountGroupMembers().delete(toRemove);
    for (AccountGroupMember m : toRemove) {
      accountCache.evict(m.getAccountId());
    }

    return Response.none();
  }

  private void writeAudits(List<AccountGroupMember> toRemove) {
    final Account.Id me = self.get().getAccountId();
    auditService.dispatchDeleteAccountsFromGroup(me, toRemove);
  }

  private Map<Account.Id, AccountGroupMember> getMembers(AccountGroup.Id groupId)
      throws OrmException {
    final Map<Account.Id, AccountGroupMember> members = new HashMap<>();
    for (AccountGroupMember m : db.get().accountGroupMembers().byGroup(groupId)) {
      members.put(m.getAccountId(), m);
    }
    return members;
  }

  @Singleton
  static class DeleteMember implements RestModifyView<MemberResource, DeleteMember.Input> {
    static class Input {}

    private final Provider<DeleteMembers> delete;

    @Inject
    DeleteMember(Provider<DeleteMembers> delete) {
      this.delete = delete;
    }

    @Override
    public Response<?> apply(MemberResource resource, Input input)
        throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
            IOException {
      AddMembers.Input in = new AddMembers.Input();
      in._oneMember = resource.getMember().getAccountId().toString();
      return delete.get().apply(resource, in);
    }
  }
}
