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
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddIncludedGroups.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class DeleteIncludedGroups implements RestModifyView<GroupResource, Input> {
  private final GroupsCollection groupsCollection;
  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  DeleteIncludedGroups(
      GroupsCollection groupsCollection,
      Provider<ReviewDb> db,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupsCollection = groupsCollection;
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
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
    if (!control.canRemoveGroup()) {
      throw new AuthException(
          String.format("Cannot delete groups from group %s", internalGroup.getName()));
    }

    Set<AccountGroup.UUID> internalGroupsToRemove = new HashSet<>();
    for (String includedGroup : input.groups) {
      GroupDescription.Basic d = groupsCollection.parse(includedGroup);
      internalGroupsToRemove.add(d.getGroupUUID());
    }

    groupsUpdateProvider
        .get()
        .deleteIncludedGroups(db.get(), internalGroup.getGroupUUID(), internalGroupsToRemove);

    return Response.none();
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
