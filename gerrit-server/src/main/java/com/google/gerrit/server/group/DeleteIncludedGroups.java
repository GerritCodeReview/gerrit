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
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.group.AddIncludedGroups.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class DeleteIncludedGroups implements RestModifyView<GroupResource, Input> {
  private final GroupsCollection groupsCollection;
  private final GroupIncludeCache groupIncludeCache;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> self;
  private final AuditService auditService;

  @Inject
  DeleteIncludedGroups(
      GroupsCollection groupsCollection,
      GroupIncludeCache groupIncludeCache,
      Provider<ReviewDb> db,
      Provider<CurrentUser> self,
      AuditService auditService) {
    this.groupsCollection = groupsCollection;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.self = self;
    this.auditService = auditService;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException {
    AccountGroup internalGroup = resource.toAccountGroup();
    if (internalGroup == null) {
      throw new MethodNotAllowedException();
    }
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    final Map<AccountGroup.UUID, AccountGroupById> includedGroups =
        getIncludedGroups(internalGroup.getId());
    final List<AccountGroupById> toRemove = new ArrayList<>();

    for (String includedGroup : input.groups) {
      GroupDescription.Basic d = groupsCollection.parse(includedGroup);
      if (!control.canRemoveGroup()) {
        throw new AuthException(String.format("Cannot delete group: %s", d.getName()));
      }

      AccountGroupById g = includedGroups.remove(d.getGroupUUID());
      if (g != null) {
        toRemove.add(g);
      }
    }

    if (!toRemove.isEmpty()) {
      writeAudits(toRemove);
      db.get().accountGroupById().delete(toRemove);
      for (AccountGroupById g : toRemove) {
        groupIncludeCache.evictParentGroupsOf(g.getIncludeUUID());
      }
      groupIncludeCache.evictSubgroupsOf(internalGroup.getGroupUUID());
    }

    return Response.none();
  }

  private Map<AccountGroup.UUID, AccountGroupById> getIncludedGroups(AccountGroup.Id groupId)
      throws OrmException {
    final Map<AccountGroup.UUID, AccountGroupById> groups = new HashMap<>();
    for (AccountGroupById g : db.get().accountGroupById().byGroup(groupId)) {
      groups.put(g.getIncludeUUID(), g);
    }
    return groups;
  }

  private void writeAudits(List<AccountGroupById> toRemoved) {
    final Account.Id me = self.get().getAccountId();
    auditService.dispatchDeleteGroupsFromGroup(me, toRemoved);
  }

  @Singleton
  static class DeleteIncludedGroup
      implements RestModifyView<IncludedGroupResource, DeleteIncludedGroup.Input> {
    static class Input {}

    private final Provider<DeleteIncludedGroups> delete;

    @Inject
    DeleteIncludedGroup(Provider<DeleteIncludedGroups> delete) {
      this.delete = delete;
    }

    @Override
    public Response<?> apply(IncludedGroupResource resource, Input input)
        throws AuthException, MethodNotAllowedException, UnprocessableEntityException,
            OrmException {
      AddIncludedGroups.Input in = new AddIncludedGroups.Input();
      in.groups = ImmutableList.of(resource.getMember().get());
      return delete.get().apply(resource, in);
    }
  }
}
