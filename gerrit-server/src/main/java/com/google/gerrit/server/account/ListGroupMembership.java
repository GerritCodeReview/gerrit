// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.util.Set;

/** GroupMembership over an explicit list. */
public class ListGroupMembership implements GroupMembership {
  private final Set<AccountGroup.UUID> groups;

  public ListGroupMembership(Iterable<AccountGroup.UUID> groupIds) {
    this.groups = ImmutableSet.copyOf(groupIds);
  }

  @Override
  public boolean contains(AccountGroup.UUID groupId) {
    return groups.contains(groupId);
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds) {
    for (AccountGroup.UUID groupId : groupIds) {
      if (contains(groupId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds) {
    return Sets.intersection(ImmutableSet.copyOf(groupIds), groups);
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    return Sets.newHashSet(groups);
  }
}
