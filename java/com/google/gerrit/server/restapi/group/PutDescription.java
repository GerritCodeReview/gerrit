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
import com.google.gerrit.extensions.common.DescriptionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.UserInitiated;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Objects;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class PutDescription implements RestModifyView<GroupResource, DescriptionInput> {
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  PutDescription(@UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public Response<String> apply(GroupResource resource, DescriptionInput input)
      throws AuthException, NotInternalGroupException, ResourceNotFoundException, OrmException,
          IOException, ConfigInvalidException {
    if (input == null) {
      input = new DescriptionInput(); // Delete would set description to null.
    }

    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    String currentDescription = Strings.nullToEmpty(internalGroup.getDescription());
    String newDescription = Strings.nullToEmpty(input.description);
    if (!Objects.equals(currentDescription, newDescription)) {
      AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
      InternalGroupUpdate groupUpdate =
          InternalGroupUpdate.builder().setDescription(newDescription).build();
      try {
        groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
      } catch (NoSuchGroupException e) {
        throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
      }
    }

    return Strings.isNullOrEmpty(input.description)
        ? Response.none()
        : Response.ok(input.description);
  }
}
