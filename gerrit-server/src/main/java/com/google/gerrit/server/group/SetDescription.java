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
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.group.SetDescription.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.Collections;

public class SetDescription implements RestModifyView<GroupResource, Input> {
  static class Input {
    @DefaultInput
    String description;
  }

  private final GroupControl.Factory groupControlFactory;
  private final GroupCache groupCache;
  private final ReviewDb db;

  @Inject
  SetDescription(final GroupControl.Factory groupControlFactory,
      final GroupCache groupCache, final ReviewDb db) {
    this.groupControlFactory = groupControlFactory;
    this.groupCache = groupCache;
    this.db = db;
  }

  @Override
  public String apply(GroupResource resource, Input input)
      throws MethodNotAllowedException, AuthException, NoSuchGroupException,
      OrmException {
    if (!groupControlFactory.controlFor(resource.getGroupUUID()).isOwner()) {
      throw new AuthException("No group owner");
    }

    final GroupDescription.Basic group = resource.getGroup();
    if (!(group instanceof GroupDescription.Internal)) {
      throw new MethodNotAllowedException();
    }

    if (input == null) {
      input = new Input(); // Delete would set description to null.
    }

    final AccountGroup internalGroup = ((GroupDescription.Internal) group).getAccountGroup();
    internalGroup.setDescription(input.description);
    db.accountGroups().update(Collections.singleton(internalGroup));
    groupCache.evict(internalGroup);

    return Strings.nullToEmpty(input.description);
  }
}
