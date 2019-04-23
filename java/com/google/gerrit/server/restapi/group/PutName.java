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
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.common.NameInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
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
public class PutName implements RestModifyView<GroupResource, NameInput> {
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  PutName(@UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public String apply(GroupResource rsrc, NameInput input)
      throws NotInternalGroupException, AuthException, BadRequestException,
          ResourceConflictException, ResourceNotFoundException, IOException,
          ConfigInvalidException {
    GroupDescription.Internal internalGroup =
        rsrc.asInternalGroup().orElseThrow(NotInternalGroupException::new);
    if (!rsrc.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    } else if (input == null || Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("name is required");
    }
    String newName = input.name.trim();
    if (newName.isEmpty()) {
      throw new BadRequestException("name is required");
    }

    if (internalGroup.getName().equals(newName)) {
      return newName;
    }

    renameGroup(internalGroup, newName);
    return newName;
  }

  private void renameGroup(GroupDescription.Internal group, String newName)
      throws ResourceConflictException, ResourceNotFoundException, IOException,
          ConfigInvalidException {
    AccountGroup.UUID groupUuid = group.getGroupUUID();
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setName(AccountGroup.nameKey(newName)).build();
    try {
      groupsUpdateProvider.get().updateGroup(groupUuid, groupUpdate);
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    } catch (DuplicateKeyException e) {
      throw new ResourceConflictException("group with name " + newName + " already exists");
    }
  }
}
