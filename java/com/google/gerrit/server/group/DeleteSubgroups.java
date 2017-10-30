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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.AddSubgroups.Input;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class DeleteSubgroups implements RestModifyView<GroupResource, Input> {
  private final GroupsCollection groupsCollection;
  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  DeleteSubgroups(
      GroupsCollection groupsCollection,
      Provider<ReviewDb> db,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupsCollection = groupsCollection;
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
          ResourceNotFoundException, IOException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    if (!control.canRemoveGroup()) {
      throw new AuthException(
          String.format("Cannot delete groups from group %s", internalGroup.getName()));
    }

    Set<AccountGroup.UUID> subgroupsToRemove = new HashSet<>();
    for (String subgroupIdentifier : input.groups) {
      GroupDescription.Basic subgroup = groupsCollection.parse(subgroupIdentifier);
      subgroupsToRemove.add(subgroup.getGroupUUID());
    }

    AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
    try {
      groupsUpdateProvider.get().removeSubgroups(db.get(), groupUuid, subgroupsToRemove);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }

    return Response.none();
  }

  @Singleton
  static class DeleteSubgroup implements RestModifyView<SubgroupResource, Input> {

    private final Provider<DeleteSubgroups> delete;

    @Inject
    DeleteSubgroup(Provider<DeleteSubgroups> delete) {
      this.delete = delete;
    }

    @Override
    public Response<?> apply(SubgroupResource resource, Input input)
        throws AuthException, MethodNotAllowedException, UnprocessableEntityException, OrmException,
            ResourceNotFoundException, IOException {
      AddSubgroups.Input in = new AddSubgroups.Input();
      in.groups = ImmutableList.of(resource.getMember().get());
      return delete.get().apply(resource, in);
    }
  }
}
