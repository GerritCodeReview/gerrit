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

import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.group.PutGroup.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.Collections;

public class PutGroup implements RestModifyView<TopLevelResource, Input> {
  static class Input {
  }

  private final PerformCreateGroup.Factory performCreateGroupFactory;
  private final GroupCache groupCache;
  private final Provider<CurrentUser> self;
  private final String id;
  final boolean visibleToAll;

  PutGroup(final PerformCreateGroup.Factory performCreateGroupFactory,
      final GroupCache groupCache, final Provider<CurrentUser> self,
      final Config cfg, final String id) {
    this.performCreateGroupFactory = performCreateGroupFactory;
    this.groupCache = groupCache;
    this.self = self;
    this.visibleToAll = cfg.getBoolean("groups", "newGroupsVisibleToAll", false);
    this.id = id;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public GroupInfo apply(TopLevelResource resource, Input input)
      throws AuthException, OrmException, NameAlreadyUsedException {
    final IdentifiedUser me = ((IdentifiedUser) self.get());
    if (!me.getCapabilities().canCreateGroup()) {
      throw new AuthException("Cannot create group");
    }

    AccountGroup group = groupCache.get(new AccountGroup.NameKey(id));
    if (group != null) {
      return new GroupInfo(GroupDescriptions.forAccountGroup(group));
    }

    try {
      group = performCreateGroupFactory.create().createGroup(id, null,
          visibleToAll, null, Collections.singleton(me.getAccountId()), null);
    } catch (PermissionDeniedException e) {
      throw new AuthException(e.getMessage());
    }
    return new GroupInfo(GroupDescriptions.forAccountGroup(group));
  }
}
