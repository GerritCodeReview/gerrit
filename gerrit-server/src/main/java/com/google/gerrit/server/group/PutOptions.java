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
import com.google.gerrit.server.account.GroupCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class PutOptions implements RestModifyView<GroupResource, GroupOptionsInfo> {
  private final GroupCache groupCache;
  private final Provider<ReviewDb> db;

  @Inject
  PutOptions(GroupCache groupCache, Provider<ReviewDb> db) {
    this.groupCache = groupCache;
    this.db = db;
  }

  @Override
  public GroupOptionsInfo apply(GroupResource resource, GroupOptionsInfo input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
          ResourceNotFoundException, OrmException {
    if (resource.toAccountGroup() == null) {
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

    AccountGroup group = db.get().accountGroups().get(resource.toAccountGroup().getId());
    if (group == null) {
      throw new ResourceNotFoundException();
    }

    group.setVisibleToAll(input.visibleToAll);
    db.get().accountGroups().update(Collections.singleton(group));
    groupCache.evict(group);

    GroupOptionsInfo options = new GroupOptionsInfo();
    if (group.isVisibleToAll()) {
      options.visibleToAll = true;
    }
    return options;
  }
}
