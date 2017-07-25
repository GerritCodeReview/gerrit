// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class Groups {

  public Optional<AccountGroup> get(ReviewDb db, AccountGroup.Id groupId) throws OrmException {
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  public Optional<AccountGroup> get(ReviewDb db, AccountGroup.UUID groupUuid) throws OrmException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(accountGroups));
    } else if (accountGroups.isEmpty()) {
      return Optional.empty();
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  public Optional<AccountGroup> get(ReviewDb db, AccountGroup.NameKey groupName)
      throws OrmException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    if (accountGroupName == null) {
      return Optional.empty();
    }

    AccountGroup.Id groupId = accountGroupName.getId();
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  public ImmutableList<AccountGroup> getAll(ReviewDb db) throws OrmException {
    return ImmutableList.copyOf(db.accountGroups().all());
  }
}
