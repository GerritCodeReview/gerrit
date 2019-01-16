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

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutOptions implements RestModifyView<GroupResource, GroupOptionsInfo> {
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  PutOptions(@UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public GroupOptionsInfo apply(GroupResource resource, GroupOptionsInfo input)
      throws NotInternalGroupException, AuthException, BadRequestException,
          ResourceNotFoundException, IOException, ConfigInvalidException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null) {
      throw new BadRequestException("options are required");
    }
    if (input.visibleToAll == null) {
      input.visibleToAll = false;
    }

    if (internalGroup.isVisibleToAll() != input.visibleToAll) {
      AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
      InternalGroupUpdate groupUpdate =
          InternalGroupUpdate.builder().setVisibleToAll(input.visibleToAll).build();
      try {
        groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
      } catch (NoSuchGroupException e) {
        throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
      }
    }

    GroupOptionsInfo options = new GroupOptionsInfo();
    if (input.visibleToAll) {
      options.visibleToAll = true;
    }
    return options;
  }
}
