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
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.PutName.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class PutName implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput public String name;
  }

  private final Provider<ReviewDb> db;
  private final Provider<GroupsUpdate> groupsUpdateProvider;

  @Inject
  PutName(Provider<ReviewDb> db, @UserInitiated Provider<GroupsUpdate> groupsUpdateProvider) {
    this.db = db;
    this.groupsUpdateProvider = groupsUpdateProvider;
  }

  @Override
  public String apply(GroupResource rsrc, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
          ResourceConflictException, ResourceNotFoundException, OrmException, IOException {
    if (rsrc.toAccountGroup() == null) {
      throw new MethodNotAllowedException();
    } else if (!rsrc.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    } else if (input == null || Strings.isNullOrEmpty(input.name)) {
      throw new BadRequestException("name is required");
    }
    String newName = input.name.trim();
    if (newName.isEmpty()) {
      throw new BadRequestException("name is required");
    }

    if (rsrc.toAccountGroup().getName().equals(newName)) {
      return newName;
    }

    renameGroup(rsrc.toAccountGroup(), newName);
    return newName;
  }

  private void renameGroup(AccountGroup group, String newName)
      throws ResourceConflictException, ResourceNotFoundException, OrmException, IOException {
    AccountGroup.UUID groupUuid = group.getGroupUUID();
    try {
      groupsUpdateProvider
          .get()
          .renameGroup(db.get(), groupUuid, new AccountGroup.NameKey(newName));
    } catch (NoSuchGroupException e) {
      throw new ResourceNotFoundException(String.format("Group %s not found", groupUuid));
    } catch (NameAlreadyUsedException e) {
      throw new ResourceConflictException("group with name " + newName + " already exists");
    }
  }
}
