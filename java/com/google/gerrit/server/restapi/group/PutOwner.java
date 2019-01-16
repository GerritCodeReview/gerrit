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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.api.groups.OwnerInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutOwner implements RestModifyView<GroupResource, OwnerInput> {
  private final GroupResolver groupResolver;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final GroupJson json;

  @Inject
  PutOwner(
      GroupResolver groupResolver,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      GroupJson json) {
    this.groupResolver = groupResolver;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.json = json;
  }

  @Override
  public GroupInfo apply(GroupResource resource, OwnerInput input)
      throws ResourceNotFoundException, NotInternalGroupException, AuthException,
          BadRequestException, UnprocessableEntityException, IOException, ConfigInvalidException,
          PermissionBackendException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null || Strings.isNullOrEmpty(input.owner)) {
      throw new BadRequestException("owner is required");
    }

    GroupDescription.Basic owner = groupResolver.parse(input.owner);
    if (!internalGroup.getOwnerGroupUUID().equals(owner.getGroupUUID())) {
      AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
      InternalGroupUpdate groupUpdate =
          InternalGroupUpdate.builder().setOwnerGroupUUID(owner.getGroupUUID()).build();
      try {
        groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
      } catch (NoSuchGroupException e) {
        throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
      }
    }
    return json.format(owner);
  }
}
