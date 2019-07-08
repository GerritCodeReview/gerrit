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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class GroupDetailFactory implements Callable<GroupDetail> {
  public interface Factory {
    GroupDetailFactory create(AccountGroup.Id groupId);
  }

  private final ReviewDb db;
  private final GroupControl.Factory groupControl;
  private final GroupCache groupCache;

  private final AccountGroup.Id groupId;
  private GroupControl control;

  @Inject
  GroupDetailFactory(
      ReviewDb db,
      GroupControl.Factory groupControl,
      GroupCache groupCache,
      @Assisted AccountGroup.Id groupId) {
    this.db = db;
    this.groupControl = groupControl;
    this.groupCache = groupCache;

    this.groupId = groupId;
  }

  @Override
  public GroupDetail call() throws OrmException, NoSuchGroupException {
    control = groupControl.validateFor(groupId);
    AccountGroup group = groupCache.get(groupId);
    GroupDetail detail = new GroupDetail();
    detail.setGroup(group);
    detail.setMembers(loadMembers());
    detail.setIncludes(loadIncludes());
    return detail;
  }

  private List<AccountGroupMember> loadMembers() throws OrmException {
    List<AccountGroupMember> members = new ArrayList<>();
    for (AccountGroupMember m : db.accountGroupMembers().byGroup(groupId)) {
      if (control.canSeeMember(m.getAccountId())) {
        members.add(m);
      }
    }
    return members;
  }

  private List<AccountGroupById> loadIncludes() throws OrmException {
    List<AccountGroupById> groups = new ArrayList<>();

    for (AccountGroupById m : db.accountGroupById().byGroup(groupId)) {
      if (control.canSeeGroup()) {
        groups.add(m);
      }
    }

    return groups;
  }
}
