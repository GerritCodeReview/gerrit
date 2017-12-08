// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.audit;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.sql.Timestamp;
import java.util.Collection;

@ExtensionPoint
public interface GroupMemberAuditListener {

  /**
   * Triggered when accounts are added to a group.
   *
   * @param actor the {@link Account.Id} of the user who adds these accounts to the group.
   * @param addedAccounts the {@link Account.Id} collection added to the group.
   * @param groupId the {@link AccountGroup.Id} of the group these accounts are added to.
   * @param addedOn the {@link Timestamp} of the add event.
   */
  void onAddAccountsToGroup(
      Account.Id actor,
      Collection<Account.Id> addedAccounts,
      AccountGroup.Id groupId,
      Timestamp addedOn);

  /**
   * Triggered when accounts are deleted from a group.
   *
   * @param actor the {@link Account.Id} of the user who deletes these accounts from the group.
   * @param deletedAccounts the {@link Account.Id} collection deleted from the group.
   * @param groupId the {@link AccountGroup.Id} of the group these accounts are deleted from.
   * @param deletedOn the {@link Timestamp} of the delete event.
   */
  void onDeleteAccountsFromGroup(
      Account.Id actor,
      Collection<Account.Id> deletedAccounts,
      AccountGroup.Id groupId,
      Timestamp deletedOn);

  /**
   * Triggered when subgroups are added to a group.
   *
   * @param actor the {@link Account.Id} of the user who adds these subgroups to the group.
   * @param addedSubgroups the {@link AccountGroup.UUID} collection added to the group.
   * @param parentGroupId the {@link AccountGroup.Id} of the group these subgroups are added to.
   * @param addedOn the {@link Timestamp} of the add event.
   */
  void onAddGroupsToGroup(
      Account.Id actor,
      Collection<AccountGroup.UUID> addedSubgroups,
      AccountGroup.Id parentGroupId,
      Timestamp addedOn);

  /**
   * Triggered when subgroups are deleted from a group.
   *
   * @param actor the {@link Account.Id} of the user who deletes these subgroups from the group.
   * @param deletedSubgroups the {@link AccountGroup.UUID} collection deleted from the group.
   * @param parentGroupId the {@link AccountGroup.Id} of the group these subgroups are deleted from.
   * @param deletedOn the {@link Timestamp} of the delete event.
   */
  void onDeleteGroupsFromGroup(
      Account.Id actor,
      Collection<AccountGroup.UUID> deletedSubgroups,
      AccountGroup.Id parentGroupId,
      Timestamp deletedOn);
}
