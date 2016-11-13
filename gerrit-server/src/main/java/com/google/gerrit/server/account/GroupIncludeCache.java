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

import com.google.gerrit.reviewdb.client.AccountGroup;
import java.util.Set;

/** Tracks group inclusions in memory for efficient access. */
public interface GroupIncludeCache {
  /** @return groups directly a member of the passed group. */
  Set<AccountGroup.UUID> subgroupsOf(AccountGroup.UUID group);

  /** @return any groups the passed group belongs to. */
  Set<AccountGroup.UUID> parentGroupsOf(AccountGroup.UUID groupId);

  /** @return set of any UUIDs that are not internal groups. */
  Set<AccountGroup.UUID> allExternalMembers();

  void evictSubgroupsOf(AccountGroup.UUID groupId);

  void evictParentGroupsOf(AccountGroup.UUID groupId);
}
