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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.group.AddIncludedGroups.Input;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;
import java.util.Map;

public class DeleteIncludedGroups implements RestModifyView<GroupResource, Input> {
  private final Provider<GroupsCollection> groupsCollection;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;
  private final Provider<CurrentUser> self;

  @Inject
  DeleteIncludedGroups(Provider<GroupsCollection> groupsCollection,
      GroupIncludeCache groupIncludeCache, ReviewDb db,
      Provider<CurrentUser> self) {
    this.groupsCollection = groupsCollection;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.self = self;
  }

  @Override
  public Object apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
      UnprocessableEntityException, OrmException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    final Map<AccountGroup.UUID, AccountGroupById> includedGroups = getIncludedGroups(internalGroup.getId());
    final List<AccountGroupById> toRemove = Lists.newLinkedList();

    for (final String includedGroup : input.groups) {
      GroupDescription.Basic d = groupsCollection.get().parse(includedGroup);
      if (!control.canRemoveGroup(d.getGroupUUID())) {
        throw new AuthException(String.format("Cannot delete group: %s",
            d.getName()));
      }

      AccountGroupById g = includedGroups.remove(d.getGroupUUID());
      if (g != null) {
        toRemove.add(g);
      }
    }

    if (!toRemove.isEmpty()) {
      writeAudits(toRemove);
      db.accountGroupById().delete(toRemove);
      for (final AccountGroupById g : toRemove) {
        groupIncludeCache.evictMemberIn(g.getIncludeUUID());
      }
      groupIncludeCache.evictMembersOf(internalGroup.getGroupUUID());
    }

    return Response.none();
  }

  private Map<AccountGroup.UUID, AccountGroupById> getIncludedGroups(
      final AccountGroup.Id groupId) throws OrmException {
    final Map<AccountGroup.UUID, AccountGroupById> groups =
        Maps.newHashMap();
    for (final AccountGroupById g : db.accountGroupById().byGroup(groupId)) {
      groups.put(g.getIncludeUUID(), g);
    }
    return groups;
  }

  private void writeAudits(final List<AccountGroupById> toBeRemoved)
      throws OrmException {
    final Account.Id me = ((IdentifiedUser) self.get()).getAccountId();
    final List<AccountGroupByIdAud> auditUpdates = Lists.newLinkedList();
    for (final AccountGroupById g : toBeRemoved) {
      AccountGroupByIdAud audit = null;
      for (AccountGroupByIdAud a : db
          .accountGroupByIdAud().byGroupInclude(g.getGroupId(),
              g.getIncludeUUID())) {
        if (a.isActive()) {
          audit = a;
          break;
        }
      }

      if (audit != null) {
        audit.removed(me, TimeUtil.nowTs());
        auditUpdates.add(audit);
      }
    }
    db.accountGroupByIdAud().update(auditUpdates);
  }

  static class DeleteIncludedGroup implements
      RestModifyView<IncludedGroupResource, DeleteIncludedGroup.Input> {
    static class Input {
    }

    private final Provider<DeleteIncludedGroups> delete;

    @Inject
    DeleteIncludedGroup(final Provider<DeleteIncludedGroups> delete) {
      this.delete = delete;
    }

    @Override
    public Object apply(IncludedGroupResource resource, Input input)
        throws MethodNotAllowedException, AuthException, BadRequestException,
        UnprocessableEntityException, OrmException {
      AddIncludedGroups.Input in = new AddIncludedGroups.Input();
      in.groups = ImmutableList.of(resource.getMember().get());
      return delete.get().apply(resource, in);
    }
  }
}
