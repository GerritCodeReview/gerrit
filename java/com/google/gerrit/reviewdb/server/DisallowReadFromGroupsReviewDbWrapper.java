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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

public class DisallowReadFromGroupsReviewDbWrapper extends ReviewDbWrapper {
  private static final String MSG = "This table has been migrated to NoteDb";

  private final Groups groups;
  private final GroupNames groupNames;
  private final GroupMembers groupMembers;
  private final GroupMemberAudits groupMemberAudits;
  private final ByIds byIds;
  private final ByIdAudits byIdAudits;

  public DisallowReadFromGroupsReviewDbWrapper(ReviewDb db) {
    super(db);
    groups = new Groups(delegate.accountGroups());
    groupNames = new GroupNames(delegate.accountGroupNames());
    groupMembers = new GroupMembers(delegate.accountGroupMembers());
    groupMemberAudits = new GroupMemberAudits(delegate.accountGroupMembersAudit());
    byIds = new ByIds(delegate.accountGroupById());
    byIdAudits = new ByIdAudits(delegate.accountGroupByIdAud());
  }

  public ReviewDb unsafeGetDelegate() {
    return delegate;
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
    return groupMembers;
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    return groupMemberAudits;
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    return byIds;
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    return byIdAudits;
  }

  private static class Groups extends AccountGroupAccessWrapper {
    protected Groups(AccountGroupAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroup> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroup, OrmException> getAsync(
        AccountGroup.Id key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroup> get(Iterable<AccountGroup.Id> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroup get(AccountGroup.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroup> byUUID(AccountGroup.UUID uuid) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroup> all() {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class GroupNames extends AccountGroupNameAccessWrapper {
    protected GroupNames(AccountGroupNameAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroupName> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupName, OrmException> getAsync(
        AccountGroup.NameKey key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupName> get(Iterable<AccountGroup.NameKey> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroupName get(AccountGroup.NameKey name) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupName> all() {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class GroupMembers extends AccountGroupMemberAccessWrapper {
    protected GroupMembers(AccountGroupMemberAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroupMember> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupMember, OrmException>
        getAsync(AccountGroupMember.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMember> get(Iterable<AccountGroupMember.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroupMember get(AccountGroupMember.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMember> byAccount(Account.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMember> byGroup(AccountGroup.Id id) {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class GroupMemberAudits extends AccountGroupMemberAuditAccessWrapper {
    protected GroupMemberAudits(AccountGroupMemberAuditAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupMemberAudit, OrmException>
        getAsync(AccountGroupMemberAudit.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> get(Iterable<AccountGroupMemberAudit.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroupMemberAudit get(AccountGroupMemberAudit.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroupAccount(
        AccountGroup.Id groupId, Account.Id accountId) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupMemberAudit> byGroup(AccountGroup.Id groupId) {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class ByIds extends AccountGroupByIdAccessWrapper {
    protected ByIds(AccountGroupByIdAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroupById> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupById, OrmException> getAsync(
        AccountGroupById.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupById> get(Iterable<AccountGroupById.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroupById get(AccountGroupById.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupById> byIncludeUUID(AccountGroup.UUID uuid) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupById> byGroup(AccountGroup.Id id) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupById> all() {
      throw new UnsupportedOperationException(MSG);
    }
  }

  private static class ByIdAudits extends AccountGroupByIdAudAccessWrapper {
    protected ByIdAudits(AccountGroupByIdAudAccess delegate) {
      super(delegate);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> iterateAllEntities() {
      throw new UnsupportedOperationException(MSG);
    }

    @SuppressWarnings("deprecation")
    @Override
    public com.google.common.util.concurrent.CheckedFuture<AccountGroupByIdAud, OrmException>
        getAsync(AccountGroupByIdAud.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> get(Iterable<AccountGroupByIdAud.Key> keys) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public AccountGroupByIdAud get(AccountGroupByIdAud.Key key) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroupInclude(
        AccountGroup.Id groupId, AccountGroup.UUID incGroupUUID) {
      throw new UnsupportedOperationException(MSG);
    }

    @Override
    public ResultSet<AccountGroupByIdAud> byGroup(AccountGroup.Id groupId) {
      throw new UnsupportedOperationException(MSG);
    }
  }
}
