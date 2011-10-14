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

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.concurrent.Callable;

public class AddGroupInclude implements Callable<GroupDetail> {

  public interface Factory {
    AddGroupInclude create(AccountGroup.Id groupId, String groupName);
  }

  private final IdentifiedUser currentUser;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final ReviewDb db;

  private final AccountGroup.Id groupId;
  private final String groupName;

  @Inject
  AddGroupInclude(final IdentifiedUser currentUser,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory,
      final GroupCache groupCache, final GroupIncludeCache groupIncludeCache,
      final ReviewDb db, final @Assisted AccountGroup.Id groupId,
      final @Assisted String groupName) {
    this.currentUser = currentUser;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.db = db;
    this.groupId = groupId;
    this.groupName = groupName;
  }

  @Override
  public GroupDetail call() throws Exception {
    final GroupControl control = groupControlFactory.validateFor(groupId);
    if (control.getAccountGroup().getType() != AccountGroup.Type.INTERNAL) {
      throw new NameAlreadyUsedException();
    }

    final AccountGroup a = findGroup(groupName);
    if (!control.canAddGroup(a.getId())) {
      throw new NoSuchEntityException();
    }

    final AccountGroupInclude.Key key =
        new AccountGroupInclude.Key(groupId, a.getId());
    AccountGroupInclude m = db.accountGroupIncludes().get(key);
    if (m == null) {
      m = new AccountGroupInclude(key);
      db.accountGroupIncludesAudit().insert(
          Collections.singleton(new AccountGroupIncludeAudit(m, currentUser
              .getAccountId())));
      db.accountGroupIncludes().insert(Collections.singleton(m));
      groupIncludeCache.evictInclude(a.getGroupUUID());
    }

    return groupDetailFactory.create(groupId).call();
  }

  private AccountGroup findGroup(final String name) throws OrmException,
      NoSuchGroupException {
    final AccountGroup g = groupCache.get(new AccountGroup.NameKey(name));
    if (g == null) {
      throw new NoSuchGroupException(name);
    }
    return g;
  }
}
