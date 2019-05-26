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
// limitations under the License.

package com.google.gerrit.server.account;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.util.Collection;

/** Tracks group inclusions in memory for efficient access. */
public interface GroupIncludeCache {

  /**
   * Returns the UUIDs of all groups of which the specified account is a direct member.
   *
   * @param memberId the ID of the account
   * @return the UUIDs of all groups having the account as member
   */
  Collection<AccountGroup.UUID> getGroupsWithMember(Account.Id memberId);

  /**
   * Returns the parent groups of a subgroup.
   *
   * @param groupId the UUID of the subgroup
   * @return the UUIDs of all direct parent groups
   */
  Collection<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId);

  /** @return set of any UUIDs that are not internal groups. */
  Collection<AccountGroup.UUID> allExternalMembers();

  void evictGroupsWithMember(Account.Id memberId);

  void evictParentGroupsOf(AccountGroup.UUID groupId);
}
