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

package com.google.gerrit.pgm.init;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

public class GroupsOnInit {

  public AccountGroup getGroup(ReviewDb db, AccountGroup.NameKey groupName) throws OrmException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    AccountGroup.Id groupId = accountGroupName.getId();
    return db.accountGroups().get(groupId);
  }

  public void addGroupMember(ReviewDb db, AccountGroup.Id groupId, Account.Id accountId)
      throws OrmException {
    db.accountGroupMembers()
        .insert(
            ImmutableList.of(
                new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId))));
  }
}
