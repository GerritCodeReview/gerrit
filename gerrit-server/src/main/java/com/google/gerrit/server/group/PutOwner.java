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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.OwnerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class PutOwner implements RestModifyView<GroupResource, OwnerInput> {
  private final GroupsCollection groupsCollection;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final Provider<ReviewDb> db;
  private final GroupJson json;

  @Inject
  PutOwner(
      GroupsCollection groupsCollection,
      @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      Provider<ReviewDb> db,
      GroupJson json) {
    this.groupsCollection = groupsCollection;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.db = db;
    this.json = json;
  }

  @Override
  public GroupInfo apply(GroupResource resource, OwnerInput input)
      throws ResourceNotFoundException, MethodNotAllowedException, AuthException,
          BadRequestException, UnprocessableEntityException, OrmException, IOException {
    GroupDescription.Internal internalGroup =
        resource.asInternalGroup().orElseThrow(MethodNotAllowedException::new);
    if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null || Strings.isNullOrEmpty(input.owner)) {
      throw new BadRequestException("owner is required");
    }

    GroupDescription.Basic owner = groupsCollection.parse(input.owner);
    if (!internalGroup.getOwnerGroupUUID().equals(owner.getGroupUUID())) {
      AccountGroup.UUID groupUuid = internalGroup.getGroupUUID();
      try {
        groupsUpdateProvider
            .get()
            .updateGroup(
                db.get(), groupUuid, group -> group.setOwnerGroupUUID(owner.getGroupUUID()));
      } catch (NoSuchGroupException e) {
        throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
      }
    }
    return json.format(owner);
  }
}
