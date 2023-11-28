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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public interface StarredChangesReader {
  boolean isStarred(Account.Id accountId, Change.Id changeId);

  /**
   * Returns a subset of change IDs among the input {@code changeIds} list that are starred by the
   * {@code caller} user.
   */
  Set<Change.Id> areStarred(Repository allUsersRepo, List<Change.Id> changeIds, Account.Id caller);

  ImmutableMap<Account.Id, Ref> byChange(Change.Id changeId);

  ImmutableSet<Change.Id> byAccountId(Account.Id accountId);

  ImmutableSet<Change.Id> byAccountId(Account.Id accountId, boolean skipInvalidChanges);
}
