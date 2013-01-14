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
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.group.PutMember.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DeleteMember implements RestModifyView<GroupResource, Input> {
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteMember(final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountResolver accountResolver, final AccountCache accountCache,
      final ReviewDb db, final Provider<CurrentUser> self) {
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.db = db;
    this.self = self;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(GroupResource resource, Input input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    if (input == null) {
      input = new Input();
    }

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    final GroupControl control = groupControlFactory.controlFor(internalGroup);
    final Set<Account.Id> currentMembers = getMembers(internalGroup.getId());
    final List<AccountGroupMember> membersToBeRemoved = Lists.newLinkedList();
    final List<String> errors = Lists.newLinkedList();

    for (final String nameOrEmail : input.members) {
      Account a = accountResolver.find(nameOrEmail);
      if (a == null) {
        errors.add((new NoSuchAccountException(nameOrEmail)).getMessage());
        continue;
      }

      if (!control.canRemoveMember(a.getId())) {
        throw new AuthException("Cannot delete member: " + a.getFullName());
      }

      if (!currentMembers.contains(a.getId())) {
        // the user to be removed from the group is not a member of the group
        // -> ignore this user
        continue;
      }

      membersToBeRemoved.add(new AccountGroupMember(
          new AccountGroupMember.Key(a.getId(), internalGroup.getId())));
    }

    failWithBadRequestOnError(errors);

    writeAudits(membersToBeRemoved);
    db.accountGroupMembers().delete(membersToBeRemoved);
    for (final AccountGroupMember m : membersToBeRemoved) {
      accountCache.evict(m.getAccountId());
    }

    return Response.none();
  }

  private void writeAudits(final List<AccountGroupMember> toBeRemoved)
      throws OrmException {
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();
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
        audit.removed(me);
        db.accountGroupMembersAudit().update(Collections.singleton(audit));
      } else {
        audit = new AccountGroupMemberAudit(m, me);
        audit.removedLegacy();
        db.accountGroupMembersAudit().insert(Collections.singleton(audit));
      }
    }
  }

  private Set<Account.Id> getMembers(final AccountGroup.Id groupId)
      throws OrmException, NoSuchGroupException {
    final Set<Account.Id> members = Sets.newHashSet();
    final GroupDetail groupDetail = groupDetailFactory.create(groupId).call();
    if (groupDetail.members != null) {
      for (final AccountGroupMember m : groupDetail.members) {
        members.add(m.getAccountId());
      }
    }
    return members;
  }

  private static void failWithBadRequestOnError(final List<String> errors)
      throws BadRequestException {
    if (errors.isEmpty()) {
      return;
    }

    if (errors.size() == 1) {
      throw new BadRequestException(errors.get(0));
    }

    final StringBuilder b = new StringBuilder();
    b.append("Multiple errors on removing group members:");
    for (final String error : errors) {
      b.append("\n");
      b.append(error);
    }
    throw new BadRequestException(b.toString());
  }
}
