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

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
public class Groups {

  public AccountGroup getExistingGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    Optional<AccountGroup> group = getGroup(db, groupUuid);
    return group.orElseThrow(() -> new NoSuchGroupException(groupUuid));
  }

  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.Id groupId) throws OrmException {
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(accountGroups));
    } else if (accountGroups.isEmpty()) {
      return Optional.empty();
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.NameKey groupName)
      throws OrmException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    if (accountGroupName == null) {
      return Optional.empty();
    }

    AccountGroup.Id groupId = accountGroupName.getId();
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  public Stream<AccountGroup> getAll(ReviewDb db) throws OrmException {
    return Streams.stream(db.accountGroups().all());
  }

  public boolean isMember(ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, group.getId());
    return db.accountGroupMembers().get(key) != null;
  }

  public boolean isIncluded(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, AccountGroup.UUID includedGroupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup parentGroup = getExistingGroup(db, parentGroupUuid);
    AccountGroupById.Key key = new AccountGroupById.Key(parentGroup.getId(), includedGroupUuid);
    return db.accountGroupById().get(key) != null;
  }

  public Stream<Account.Id> getMembers(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    ResultSet<AccountGroupMember> accountGroupMembers =
        db.accountGroupMembers().byGroup(group.getId());
    return Streams.stream(accountGroupMembers).map(AccountGroupMember::getAccountId);
  }

  public Stream<AccountGroup.UUID> getIncludes(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    ResultSet<AccountGroupById> accountGroupByIds = db.accountGroupById().byGroup(group.getId());
    return Streams.stream(accountGroupByIds).map(AccountGroupById::getIncludeUUID).distinct();
  }

  public Stream<AccountGroup.Id> getGroupsWithMember(ReviewDb db, Account.Id accountId)
      throws OrmException {
    ResultSet<AccountGroupMember> accountGroupMembers =
        db.accountGroupMembers().byAccount(accountId);
    return Streams.stream(accountGroupMembers).map(AccountGroupMember::getAccountGroupId);
  }

  public Stream<AccountGroup.Id> getParentGroups(ReviewDb db, AccountGroup.UUID includedGroupUuid)
      throws OrmException {
    ResultSet<AccountGroupById> accountGroupByIds =
        db.accountGroupById().byIncludeUUID(includedGroupUuid);
    return Streams.stream(accountGroupByIds).map(AccountGroupById::getGroupId);
  }

  public Stream<AccountGroup.UUID> getExternalGroups(ReviewDb db) throws OrmException {
    return Streams.stream(db.accountGroupById().all())
        .map(AccountGroupById::getIncludeUUID)
        .distinct()
        .filter(groupUuid -> !AccountGroup.isInternalGroup(groupUuid));
  }
}
