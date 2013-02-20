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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.PutOwner.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

public class PutOwner implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String owner;
  }

  private final Provider<GroupsCollection> groupsCollection;
  private final GroupCache groupCache;
  private final GroupControl.Factory controlFactory;
  private final ReviewDb db;

  @Inject
  PutOwner(Provider<GroupsCollection> groupsCollection, GroupCache groupCache,
      GroupControl.Factory controlFactory, ReviewDb db) {
    this.groupsCollection = groupsCollection;
    this.groupCache = groupCache;
    this.controlFactory = controlFactory;
    this.db = db;
  }

  @Override
  public GroupInfo apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
      ResourceNotFoundException, OrmException {
    AccountGroup group = resource.toAccountGroup();
    if (group == null) {
      throw new MethodNotAllowedException();
    } else if (!resource.getControl().isOwner()) {
      throw new AuthException("Not group owner");
    }

    if (input == null || Strings.isNullOrEmpty(input.owner)) {
      throw new BadRequestException("owner is required");
    }

    GroupDescription.Basic owner;
    try {
      owner = groupsCollection.get().parse(input.owner);
    } catch (ResourceNotFoundException e) {
      throw new BadRequestException(String.format("No such group: %s", input.owner));
    }

    try {
      GroupControl c = controlFactory.validateFor(owner.getGroupUUID());
      group = db.accountGroups().get(group.getId());
      if (group == null) {
        throw new ResourceNotFoundException();
      }

      if (!group.getOwnerGroupUUID().equals(owner.getGroupUUID())) {
        group.setOwnerGroupUUID(owner.getGroupUUID());
        db.accountGroups().update(Collections.singleton(group));
        groupCache.evict(group);
      }
      return new GroupInfo(c.getGroup());
    } catch (NoSuchGroupException e) {
      throw new BadRequestException(String.format("No such group: %s", input.owner));
    }
  }
}
