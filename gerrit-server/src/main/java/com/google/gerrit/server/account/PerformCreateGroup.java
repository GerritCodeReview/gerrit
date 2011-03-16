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
// limitations under the License

package com.google.gerrit.server.account;

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gwtorm.client.OrmException;

public interface PerformCreateGroup {

  /**
   * Creates a new group.
   *
   * @param groupName the name for the new group
   * @param groupDescription the description of the new group, <code>null</code>
   *        if no description
   * @param ownerGroupId the group that should own the new group, if
   *        <code>null</code> the new group will own itself
   * @param initialMembers initial members to be added to the new group
   * @return the id of the new group
   * @throws OrmException is thrown in case of any data store read or write
   *         error
   * @throws NameAlreadyUsedException is thrown in case a group with the given
   *         name already exists
   */
  public AccountGroup.Id createGroup(String groupName, String groupDescription,
      AccountGroup.Id ownerGroupId, Account.Id... initialMembers)
      throws OrmException, NameAlreadyUsedException;
}
