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
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupDetailFactory;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gerrit.server.group.PutName.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@Singleton
public class PutName implements RestModifyView<GroupResource, Input> {
  public static class Input {
    @DefaultInput public String name;
  }

  private final Provider<ReviewDb> db;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final Provider<IdentifiedUser> currentUser;

  @Inject
  PutName(
      Provider<ReviewDb> db,
      GroupCache groupCache,
      GroupDetailFactory.Factory groupDetailFactory,
      RenameGroupOp.Factory renameGroupOpFactory,
      Provider<IdentifiedUser> currentUser) {
    this.db = db;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.currentUser = currentUser;
  }

  @Override
  public String apply(GroupResource rsrc, Input input)
      throws MethodNotAllowedException, AuthException, BadRequestException,
          ResourceConflictException, OrmException, NoSuchGroupException {
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

    return renameGroup(rsrc.toAccountGroup(), newName).group.getName();
  }

  private GroupDetail renameGroup(AccountGroup group, String newName)
      throws ResourceConflictException, OrmException, NoSuchGroupException {
    AccountGroup.Id groupId = group.getId();
    AccountGroup.NameKey old = group.getNameKey();
    AccountGroup.NameKey key = new AccountGroup.NameKey(newName);

    try {
      AccountGroupName id = new AccountGroupName(key, groupId);
      db.get().accountGroupNames().insert(Collections.singleton(id));
    } catch (OrmException e) {
      AccountGroupName other = db.get().accountGroupNames().get(key);
      if (other != null) {
        // If we are using this identity, don't report the exception.
        //
        if (other.getId().equals(groupId)) {
          return groupDetailFactory.create(groupId).call();
        }

        // Otherwise, someone else has this identity.
        //
        throw new ResourceConflictException("group with name " + newName + "already exists");
      }
      throw e;
    }

    group.setNameKey(key);
    db.get().accountGroups().update(Collections.singleton(group));

    AccountGroupName priorName = db.get().accountGroupNames().get(old);
    if (priorName != null) {
      db.get().accountGroupNames().delete(Collections.singleton(priorName));
    }

    groupCache.evict(group);
    groupCache.evictAfterRename(old, key);
    renameGroupOpFactory
        .create(
            currentUser.get().newCommitterIdent(new Date(), TimeZone.getDefault()),
            group.getGroupUUID(),
            old.get(),
            newName)
        .start(0, TimeUnit.MILLISECONDS);

    return groupDetailFactory.create(groupId).call();
  }
}
