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

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

import java.util.Set;

public interface StarredChangesCache {

  /**
   * Checks whether the user has starred the change.
   *
   * @param accountId account ID of the user
   * @param changeId ID of the change
   * @param label star label
   * @return {@code true} if the user has starred the change with the given
   *         label, otherwise {@code false}
   */
  boolean isStarred(Account.Id accountId, Change.Id changeId, String label);

  /**
   * Returns all star labels that the user has put on the change.
   *
   * @param accountId account ID of the user
   * @param changeId ID of the change
   * @return the star labels that the user has put on the change
   */
  ImmutableSortedSet<String> getLabels(Account.Id accountId, Change.Id changeId);

  /**
   * Returns all changes that are starred by the given user with the given
   * label.
   *
   * @param accountId the account ID of the user
   * @param label star label
   * @return the changes that are starred by the given user with the given label
   */
  ImmutableSet<Change.Id> byAccount(Account.Id accountId, String label);

  /**
   * Returns all changes that are starred by the given user.
   *
   * @param accountId the account ID of the user
   * @return the changes that are starred by the given user with the star labels
   */
  ImmutableMultimap<Change.Id, String> byAccount(Account.Id accountId);

  /**
   * Returns all accounts that have starred the given change with the given
   * label.
   *
   * @param changeId the ID of the change
   * @param label star label
   * @return the accounts that have starred the given change with the star
   *         labels
   */
  ImmutableSet<Account.Id> byChange(Change.Id changeId, String label);

  /**
   * Returns all accounts that have starred the given change.
   *
   * @param changeId the ID of the change
   * @return the accounts that have starred the given change with the star
   *         labels
   */
  ImmutableMultimap<Account.Id, String> byChange(Change.Id changeId);

  /**
   * Updates the cache when a user stars/unstars a change with labels.
   *
   * @param accountId the account ID of the user that stars the change
   * @param changeId the ID of the change that is starred by the user
   * @param labels the new labels of the change, labels in this set which are
   *        not on the change yet are added, labels on the change that are not
   *        in this set are removed
   */
  void star(Account.Id accountId, Change.Id changeId, Set<String> labels);

  /**
   * Update the cache when a change is unstarred by all users (e.g. when the
   * change is deleted).
   *
   * @param changeId the ID of the change that is unstarred
   * @return the accounts for which the change was unstarred
   */
  ImmutableSet<Account.Id> unstarAll(Change.Id changeId);
}
