// Copyright (C) 2009 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.AccountGroup;

/** Tracks group objects in memory for efficient access. */
public interface GroupCache {
  AccountGroup get(AccountGroup.Id groupId);

  AccountGroup get(AccountGroup.NameKey name);

  /**
   * Lookup a group definition by its UUID. The returned definition may be null if the group has
   * been deleted and the UUID reference is stale, or was copied from another server.
   */
  @Nullable
  AccountGroup get(AccountGroup.UUID uuid);

  /** @return sorted list of groups. */
  ImmutableList<AccountGroup> all();

  /** Notify the cache that a new group was constructed. */
  void onCreateGroup(AccountGroup.NameKey newGroupName);

  void evict(AccountGroup group);

  void evictAfterRename(final AccountGroup.NameKey oldName, final AccountGroup.NameKey newName);
}
