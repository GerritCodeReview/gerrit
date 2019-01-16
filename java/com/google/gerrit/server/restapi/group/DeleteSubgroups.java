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

package com.google.gerrit.server.restapi.group;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.SubgroupResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.restapi.group.AddSubgroups.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteSubgroups implements RestModifyView<GroupResource, Input> {
  private final GroupResolver groupResolver;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  DeleteSubgroups(
      GroupResolver groupResolver, @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupResolver = groupResolver;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public Response<?> apply(GroupResource resource, Input input)
      throws AuthException, NotInternalGroupException, UnprocessableEntityException,
          StorageException, ResourceNotFoundException, IOException, ConfigInvalidException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    input = Input.init(input);

    final GroupControl control = resource.getControl();
    if (!control.canRemoveGroup()) {
      throw new AuthException(
          String.format("Cannot delete groups from group %s", internalGroup.getName()));
    }

    Set<AccountGroup.UUID> subgroupsToRemove = new HashSet<>();
    for (String subgroupIdentifier : input.groups) {
      GroupDescription.Basic subgroup = groupResolver.parse(subgroupIdentifier);
      subgroupsToRemove.add(subgroup.getGroupUUID());
    }

    AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
    try {
      removeSubgroups(groupUuid, subgroupsToRemove);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    }

    return Response.none();
  }

  private void removeSubgroups(
      AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> removedSubgroupUuids)
      throws StorageException, NoSuchGroupException, IOException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroupUuids -> Sets.difference(subgroupUuids, removedSubgroupUuids))
            .build();
    groupsUpdateProvider.get().updateGroup(parentGroupUuid, groupUpdate);
  }

  @Singleton
  public static class DeleteSubgroup implements RestModifyView<SubgroupResource, Input> {

    private final Provider<DeleteSubgroups> delete;

    @Inject
    public DeleteSubgroup(Provider<DeleteSubgroups> delete) {
      this.delete = delete;
    }

    @Override
    public Response<?> apply(SubgroupResource resource, Input input)
        throws AuthException, MethodNotAllowedException, UnprocessableEntityException,
            StorageException, ResourceNotFoundException, IOException, ConfigInvalidException {
      AddSubgroups.Input in = new AddSubgroups.Input();
      in.groups = ImmutableList.of(resource.getMember().get());
      return delete.get().apply(resource, in);
    }
  }
}
