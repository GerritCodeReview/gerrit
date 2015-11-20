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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;

public interface StarredChangesCache {
  public static final String DEFAULT_LABEL = "default";

  /**
   * Checks whether the user has starred the change with the given label.
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
  ImmutableSet<String> getLabels(Account.Id accountId, Change.Id changeId);

  /**
   * Returns all changes that are starred by the given user with the given
   * label.
   *
   * @param accountId the account ID of the user
   * @param label star label
   * @return the changes that are starred by the given user with the given label
   */
  Iterable<Change.Id> byAccount(Account.Id accountId, String label);

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
  Iterable<Account.Id> byChange(Change.Id changeId, String label);

  /**
   * Returns all accounts that have starred the given change.
   *
   * @param changeId the ID of the change
   * @return the accounts that have starred the given change with the star
   *         labels
   */
  ImmutableMultimap<Account.Id, String> byChange(Change.Id changeId);

  /**
   * Updates the cache when a user stars a change with a label.
   *
   * @param accountId the account ID of the user that stars the change
   * @param changeId the ID of the change that is starred by the user
   * @param labelList the labels that should be applied to the change
   * @return the star labels of the user on the change
   */
  ImmutableSet<String> star(Account.Id accountId, Change.Id changeId,
      String... labelList);

  /**
   * Updates the cache when a user removes a star label from a change.
   *
   * @param accountId the account ID of the user that unstars the change
   * @param changeId the ID of the change that is unstarred by the user
   * @param labelList the labels that should be removed from the change
   * @return the star labels of the user on the change
   */
  ImmutableSet<String> unstar(Account.Id accountId, Change.Id changeId,
      String... labelList);

  /**
   * Update the cache when a change is unstarred by all users (e.g. when the
   * change is deleted).
   *
   * @param changeId the ID of the change that is unstarred
   * @return the accounts for which the change was unstarred
   */
  Iterable<Account.Id> unstarAll(Change.Id changeId);
}
