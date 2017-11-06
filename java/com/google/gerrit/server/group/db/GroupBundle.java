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

package com.google.gerrit.server.group.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

/** A bundle of all entities rooted at a single {@link AccountGroup} entity. */
@AutoValue
public abstract class GroupBundle {
  public static GroupBundle fromReviewDb(ReviewDb db, AccountGroup.Id id) throws OrmException {
    AccountGroup group = db.accountGroups().get(id);
    if (group == null) {
      throw new OrmException("Group " + id + " not found");
    }
    return create(
        group,
        db.accountGroupMembers().byGroup(id),
        db.accountGroupMembersAudit().byGroup(id),
        db.accountGroupById().byGroup(id),
        db.accountGroupByIdAud().byGroup(id));
  }

  public static GroupBundle create(
      AccountGroup group,
      Iterable<AccountGroupMember> members,
      Iterable<AccountGroupMemberAudit> memberAudit,
      Iterable<AccountGroupById> byId,
      Iterable<AccountGroupByIdAud> byIdAudit) {
    return create(
        group,
        ImmutableList.copyOf(members),
        ImmutableList.copyOf(memberAudit),
        ImmutableList.copyOf(byId),
        ImmutableList.copyOf(byIdAudit));
  }

  public AccountGroup.Id id() {
    return group().getId();
  }

  public abstract AccountGroup group();

  public abstract ImmutableList<AccountGroupMember> members();

  public abstract ImmutableList<AccountGroupMemberAudit> memberAudit();

  public abstract ImmutableList<AccountGroupById> byId();

  public abstract ImmutableList<AccountGroupByIdAud> byIdAudit();
}
