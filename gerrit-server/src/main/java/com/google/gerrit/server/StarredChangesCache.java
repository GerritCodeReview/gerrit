// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

public interface StarredChangesCache {

  /**
   * Checks whether the user has starred the change.
   *
   * @param accountId account ID of the user
   * @param changeId ID of the change
   * @return {@code true} if the user has starred the change, otherwise
   *         {@code false}
   */
  boolean isStarred(Account.Id accountId, Change.Id changeId);

  /**
   * Returns all changes that are starred by the given user.
   *
   * @param accountId the account ID of the user
   * @return the changes that are starred by the given user
   */
  Iterable<Change.Id> byAccount(Account.Id accountId);

  /**
   * Returns all accounts that have starred the given change.
   *
   * @param changeId the ID of the change
   * @return the accounts that have starred the given change
   */
  Iterable<Account.Id> byChange(Change.Id changeId);

  /**
   * Updates the cache when a user stars a change.
   *
   * @param accountId the account ID of the user that stars the change
   * @param changeId the ID of the change that is starred by the user
   */
  void star(Account.Id accountId, Change.Id changeId);

  /**
   * Updates the cache when a user unstars a change.
   *
   * @param accountId the account ID of the user that unstars the change
   * @param changeId the ID of the change that is unstarred by the user
   */
  void unstar(Account.Id accountId, Change.Id changeId);

  /**
   * Update the cache when a change is unstarred by all users (e.g. when the
   * change is deleted).
   *
   * @param changeId the ID of the change that is unstarred
   * @return the accounts for which the change was unstarred
   */
  Iterable<Account.Id> unstarAll(Change.Id changeId);
}
