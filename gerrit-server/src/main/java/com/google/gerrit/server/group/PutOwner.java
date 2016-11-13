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
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.PutOwner.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class PutOwner implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput public String owner;
  }

  private final GroupsCollection groupsCollection;
  private final GroupCache groupCache;
  private final Provider<ReviewDb> db;
  private final GroupJson json;

  @Inject
  PutOwner(
      GroupsCollection groupsCollection,
      GroupCache groupCache,
      Provider<ReviewDb> db,
      GroupJson json) {
    this.groupsCollection = groupsCollection;
    this.groupCache = groupCache;
    this.db = db;
    this.json = json;
  }

  @Override
  public GroupInfo apply(GroupResource resource, Input input)
      throws ResourceNotFoundException, MethodNotAllowedException, AuthException,
          BadRequestException, UnprocessableEntityException, OrmException {
    AccountGroup group = resource.toAccountGroup();
    if (group == null) {
      throw new MethodNotAllowedException();
    } else if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null || Strings.isNullOrEmpty(input.owner)) {
      throw new BadRequestException("owner is required");
    }

    group = db.get().accountGroups().get(group.getId());
    if (group == null) {
      throw new ResourceNotFoundException();
    }

    GroupDescription.Basic owner = groupsCollection.parse(input.owner);
    if (!group.getOwnerGroupUUID().equals(owner.getGroupUUID())) {
      group.setOwnerGroupUUID(owner.getGroupUUID());
      db.get().accountGroups().update(Collections.singleton(group));
      groupCache.evict(group);
    }
    return json.format(owner);
  }
}
