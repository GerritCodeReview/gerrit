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
import com.google.common.collect.Iterables;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import java.util.List;

/**
 * A database accessor for calls related to groups.
 *
 * <p>All calls which read or write group related details to the database <strong>during
 * init</strong> (either ReviewDb or NoteDb) are gathered here. For non-init cases, use {@code
 * Groups} or {@code GroupsUpdate} instead.
 *
 * <p>All methods of this class refer to <em>internal</em> groups.
 */
public class GroupsOnInit {

  /**
   * Returns the {@code AccountGroup} for the specified name.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupName the name of the group
   * @return the {@code AccountGroup} which has the specified name
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   * @throws NoSuchGroupException if a group with such a name doesn't exist
   */
  public AccountGroup getExistingGroup(ReviewDb db, AccountGroup.NameKey groupName)
      throws OrmException, NoSuchGroupException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    if (accountGroupName == null) {
      throw new NoSuchGroupException(groupName.toString());
    }

    AccountGroup.Id groupId = accountGroupName.getId();
    AccountGroup group = db.accountGroups().get(groupId);
    if (group == null) {
      throw new NoSuchGroupException(groupName.toString());
    }
    return group;
  }

  /**
   * Adds an account as member to a group. The account is only added as a new member if it isn't
   * already a member of the group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the account exists! It also doesn't
   * update the account index!
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param accountId the ID of the account to add
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void addGroupMember(ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    AccountGroup.Id groupId = group.getId();

    if (isMember(db, groupId, accountId)) {
      return;
    }

    db.accountGroupMembers()
        .insert(
            ImmutableList.of(
                new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId))));
  }

  private static AccountGroup getExistingGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Iterables.getOnlyElement(accountGroups);
    } else if (accountGroups.isEmpty()) {
      throw new NoSuchGroupException(groupUuid);
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  private static boolean isMember(ReviewDb db, AccountGroup.Id groupId, Account.Id accountId)
      throws OrmException {
    AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, groupId);
    return db.accountGroupMembers().get(key) != null;
  }
}
