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

import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class PutOptions implements RestModifyView<GroupResource, GroupOptionsInfo> {
  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  PutOptions(Provider<ReviewDb> db, @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public GroupOptionsInfo apply(GroupResource resource, GroupOptionsInfo input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
          ResourceNotFoundException, OrmException, IOException {
    AccountGroup accountGroup = resource.toAccountGroup();
    if (accountGroup == null) {
      throw new MethodNotAllowedException();
    } else if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null) {
      throw new BadRequestException("options are required");
    }
    if (input.visibleToAll == null) {
      input.visibleToAll = false;
    }

    if (accountGroup.isVisibleToAll() != input.visibleToAll) {
      Optional<AccountGroup> updatedGroup =
          groupsUpdateProvider
              .get()
              .updateGroup(
                  db.get(),
                  accountGroup.getGroupUUID(),
                  group -> group.setVisibleToAll(input.visibleToAll));
      if (!updatedGroup.isPresent()) {
        throw new ResourceNotFoundException();
      }
    }

    GroupOptionsInfo options = new GroupOptionsInfo();
    if (input.visibleToAll) {
      options.visibleToAll = true;
    }
    return options;
  }
}
