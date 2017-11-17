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

package com.google.gerrit.server.schema;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.AccountGroupAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAudAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAuditAccess;
import com.google.gerrit.reviewdb.server.AccountGroupNameAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

/**
 * Wrapper for ReviewDb that never calls the underlying groups tables.
 *
 * <p>See {@link NotesMigrationSchemaFactory} for discussion.
 */
public class NoGroupsReviewDbWrapper extends ReviewDbWrapper {
  private static <T> ResultSet<T> empty() {
    return new ListResultSet<>(ImmutableList.of());
  }

  private final AccountGroupAccess groups;
  private final AccountGroupNameAccess groupNames;
  private final AccountGroupMemberAccess members;
  private final AccountGroupMemberAuditAccess memberAudits;
  private final AccountGroupByIdAccess byIds;
  private final AccountGroupByIdAudAccess byIdAudits;

  protected NoGroupsReviewDbWrapper(ReviewDb db) {
    super(db);
    this.groups = new Groups(this, delegate);
    this.groupNames = new GroupNames(this, delegate);
    this.members = new Members(this, delegate);
    this.memberAudits = new MemberAudits(this, delegate);
    this.byIds = new ByIds(this, delegate);
    this.byIdAudits = new ByIdAudits(this, delegate);
  }

  @Override
  public AccountGroupAccess accountGroups() {
    return groups;
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    return groupNames;
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    return members;
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    return memberAudits;
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    return byIdAudits;
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    return byIds;
  }

  private static class Groups extends AbstractDisabledAccess<AccountGroup, AccountGroup.Id>
      implements AccountGroupAccess {
    private Groups(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroups());
    }

    @Override
    public ResultSet<AccountGroup> byUUID(AccountGroup.UUID uuid) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroup> all() throws OrmException {
      return empty();
    }
  }

  private static class GroupNames
      extends AbstractDisabledAccess<AccountGroupName, AccountGroup.NameKey>
      implements AccountGroupNameAccess {
    private GroupNames(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroupNames());
    }

    @Override
    public ResultSet<AccountGroupName> all() throws OrmException {
      return empty();
    }
  }

  private static class Members
      extends AbstractDisabledAccess<AccountGroupMember, AccountGroupMember.Key>
      implements AccountGroupMemberAccess {
    private Members(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroupMembers());
    }

    @Override
    public ResultSet<AccountGroupMember> byAccount(Account.Id id) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroupMember> byGroup(AccountGroup.Id id) throws OrmException {
      return empty();
    }
  }

  private static class MemberAudits
      extends AbstractDisabledAccess<AccountGroupMemberAudit, AccountGroupMemberAudit.Key>
      implements AccountGroupMemberAuditAccess {
    private MemberAudits(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroupMembersAudit());
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroupAccount(
        AccountGroup.Id groupId, com.google.gerrit.reviewdb.client.Account.Id accountId)
        throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroup(AccountGroup.Id groupId) throws OrmException {
      return empty();
    }
  }

  private static class ByIds extends AbstractDisabledAccess<AccountGroupById, AccountGroupById.Key>
      implements AccountGroupByIdAccess {
    private ByIds(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroupById());
    }

    @Override
    public ResultSet<AccountGroupById> byIncludeUUID(AccountGroup.UUID uuid) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroupById> byGroup(AccountGroup.Id id) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroupById> all() throws OrmException {
      return empty();
    }
  }

  private static class ByIdAudits
      extends AbstractDisabledAccess<AccountGroupByIdAud, AccountGroupByIdAud.Key>
      implements AccountGroupByIdAudAccess {
    private ByIdAudits(ReviewDbWrapper wrapper, ReviewDb db) {
      super(wrapper, db.accountGroupByIdAud());
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroupInclude(
        AccountGroup.Id groupId, AccountGroup.UUID incGroupUUID) throws OrmException {
      return empty();
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroup(AccountGroup.Id groupId) throws OrmException {
      return empty();
    }
  }
}
