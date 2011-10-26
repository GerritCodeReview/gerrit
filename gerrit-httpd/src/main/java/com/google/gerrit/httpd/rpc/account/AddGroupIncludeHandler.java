// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupMemberResult;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AddGroupInclude;
import com.google.gerrit.server.account.GroupCache;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

public class AddGroupIncludeHandler extends Handler<GroupDetail> {

  interface Factory {
    AddGroupIncludeHandler create(AccountGroup.Id groupId, String groupName);
  }

  private final AddGroupInclude.Factory addGroupIncludeFactory;
  private final GroupCache groupCache;

  private final AccountGroup.Id groupId;
  private final String groupName;

  @Inject
  AddGroupIncludeHandler(final AddGroupInclude.Factory addGroupIncludeFactory,
      final GroupCache groupCache, final @Assisted AccountGroup.Id groupId,
      final @Assisted String groupName) {
    this.addGroupIncludeFactory = addGroupIncludeFactory;
    this.groupCache = groupCache;
    this.groupId = groupId;
    this.groupName = groupName;
  }

  @Override
  public GroupDetail call() throws Exception {
    final AccountGroup.Id a = findGroup(groupName);
    final GroupMemberResult result =
        addGroupIncludeFactory.create(groupId, Collections.singleton(a)).call();
    if (!result.getErrors().isEmpty()) {
      final GroupMemberResult.Error error = result.getErrors().get(0);
      switch (error.getType()) {
        case ADD_NOT_PERMITTED:
          throw new NoSuchEntityException();
        default:
          throw new IllegalStateException();
      }
    }
    return result.getGroup();
  }

  private AccountGroup.Id findGroup(final String name) throws OrmException,
      NoSuchGroupException {
    final AccountGroup g = groupCache.get(new AccountGroup.NameKey(name));
    if (g == null) {
      throw new NoSuchGroupException(name);
    }
    return g.getId();
  }
}
