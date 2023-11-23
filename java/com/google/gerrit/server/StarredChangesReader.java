// Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Repository;

/** Interface for reading information about starred changes. */
public interface StarredChangesReader {

  /**
   * Checks if a specific change is starred by a given user.
   *
   * @param accountId the {@code Account.Id}.
   * @param changeId the {@code Change.Id}.
   * @return {@code true} if the change is starred by the user, {@code false} otherwise.
   */
  boolean isStarred(Account.Id accountId, Change.Id changeId);

  /**
   * Returns a subset of {@code Change.Id}s among the input {@code changeIds} list that are starred
   * by the {@code caller} user.
   *
   * @param allUsersRepo 'All-Users' repository.
   * @param changeIds the list of {@code Change.Id}s to check.
   * @param caller the {@code Account.Id} to check starred changes by a user.
   * @return a set of {@code Change.Id}s that are starred by the specified user.
   */
  Set<Change.Id> areStarred(Repository allUsersRepo, List<Change.Id> changeIds, Account.Id caller);

  /**
   * Retrieves a list of {@code Account.Id} which starred a {@code Change.Id}.
   *
   * @param changeId the {@code Change.Id}.
   * @return an immutable list of {@code Account.Id}s for the specified change.
   */
  ImmutableList<Account.Id> byChange(Change.Id changeId);

  /**
   * Retrieves a set of {@code changeIds} starred by {@code Account.Id}.
   *
   * @param accountId the {@code Account.Id}.
   * @return an immutable set of {@code Change.Id}s associated with the specified user account.
   */
  ImmutableSet<Change.Id> byAccountId(Account.Id accountId);

  /**
   * Retrieves a set of {@code Change.Id}s associated with the specified user account, optionally
   * skipping invalid changes.
   *
   * @param accountId the {@code Account.Id}.
   * @param skipInvalidChanges {@code true} to skip invalid changes, {@code false} otherwise.
   * @return an immutable set of {@code Change.Id}s associated with the specified user account.
   */
  ImmutableSet<Change.Id> byAccountId(Account.Id accountId, boolean skipInvalidChanges);
}
