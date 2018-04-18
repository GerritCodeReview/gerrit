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

package com.google.gerrit.server.query.change;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import java.util.Set;

public final class SingleGroupUser extends CurrentUser {
  private final GroupMembership groups;

  public SingleGroupUser(AccountGroup.UUID groupId) {
    this(ImmutableSet.of(groupId));
  }

  public SingleGroupUser(Set<AccountGroup.UUID> groups) {
    this.groups = new ListGroupMembership(groups);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    return groups;
  }

  @Override
  public Object getCacheKey() {
    return groups.getKnownGroups();
  }
}
