// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.Groups;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.concurrent.Callable;

public class GroupDetailFactory implements Callable<GroupDetail> {
  public interface Factory {
    GroupDetailFactory create(AccountGroup.UUID groupUuid);
  }

  private final ReviewDb db;
  private final GroupControl.Factory groupControl;
  private final Groups groups;
  private final GroupIncludeCache groupIncludeCache;

  private final AccountGroup.UUID groupUuid;
  private GroupControl control;

  @Inject
  GroupDetailFactory(
      ReviewDb db,
      GroupControl.Factory groupControl,
      Groups groups,
      GroupIncludeCache groupIncludeCache,
      @Assisted AccountGroup.UUID groupUuid) {
    this.db = db;
    this.groupControl = groupControl;
    this.groups = groups;
    this.groupIncludeCache = groupIncludeCache;

    this.groupUuid = groupUuid;
  }

  @Override
  public GroupDetail call() throws OrmException, NoSuchGroupException {
    control = groupControl.validateFor(groupUuid);
    ImmutableSet<Account.Id> members = loadMembers();
    ImmutableSet<AccountGroup.UUID> includes = loadIncludes();
    return new GroupDetail(members, includes);
  }

  private ImmutableSet<Account.Id> loadMembers() throws OrmException {
    return groups.getMembers(db, groupUuid).filter(control::canSeeMember).collect(toImmutableSet());
  }

  private ImmutableSet<AccountGroup.UUID> loadIncludes() {
    if (!control.canSeeGroup()) {
      return ImmutableSet.of();
    }

    return ImmutableSet.copyOf(groupIncludeCache.subgroupsOf(groupUuid));
  }
}
