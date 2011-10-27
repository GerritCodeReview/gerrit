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
// limitations under the License

package com.google.gerrit.server.account;

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class PerformRenameGroup {

  public interface Factory {
    PerformRenameGroup create();
  }

  private final ReviewDb db;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final IdentifiedUser currentUser;

  @Inject
  PerformRenameGroup(final ReviewDb db, final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory,
      final RenameGroupOp.Factory renameGroupOpFactory,
      final IdentifiedUser currentUser) {
    this.db = db;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.currentUser = currentUser;
  }

  public GroupDetail renameGroup(final String groupName,
      final String newGroupName) throws OrmException, NameAlreadyUsedException,
      NoSuchGroupException {
    final AccountGroup.NameKey groupNameKey =
        new AccountGroup.NameKey(groupName);
    final AccountGroup group = groupCache.get(groupNameKey);
    if (group == null) {
      throw new NoSuchGroupException(groupNameKey);
    }
    return renameGroup(group.getId(), newGroupName);
  }

  public GroupDetail renameGroup(final AccountGroup.Id groupId,
      final String newName) throws OrmException, NameAlreadyUsedException,
      NoSuchGroupException {
    final GroupControl ctl = groupControlFactory.validateFor(groupId);
    final AccountGroup group = db.accountGroups().get(groupId);
    if (group == null || !ctl.isOwner()) {
      throw new NoSuchGroupException(groupId);
    }

    final AccountGroup.NameKey old = group.getNameKey();
    final AccountGroup.NameKey key = new AccountGroup.NameKey(newName);

    try {
      final AccountGroupName id = new AccountGroupName(key, groupId);
      db.accountGroupNames().insert(Collections.singleton(id));
    } catch (OrmDuplicateKeyException dupeErr) {
      // If we are using this identity, don't report the exception.
      //
      AccountGroupName other = db.accountGroupNames().get(key);
      if (other != null && other.getId().equals(groupId)) {
        return groupDetailFactory.create(groupId).call();
      }

      // Otherwise, someone else has this identity.
      //
      throw new NameAlreadyUsedException();
    }

    group.setNameKey(key);
    db.accountGroups().update(Collections.singleton(group));

    AccountGroupName priorName = db.accountGroupNames().get(old);
    if (priorName != null) {
      db.accountGroupNames().delete(Collections.singleton(priorName));
    }

    groupCache.evict(group);
    groupCache.evictAfterRename(old);
    renameGroupOpFactory.create( //
        currentUser.newCommitterIdent(new Date(), TimeZone.getDefault()), //
        group.getGroupUUID(), //
        old.get(), newName).start(0, TimeUnit.MILLISECONDS);

    return groupDetailFactory.create(groupId).call();
  }
}
